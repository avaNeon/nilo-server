import logging
import time
from typing import cast

from langchain.agents import create_agent
from langchain.agents.middleware import ToolCallLimitMiddleware, ModelCallLimitMiddleware
from langchain.agents.structured_output import ToolStrategy
from langchain_core.language_models import BaseChatModel
from langchain_core.messages import SystemMessage, HumanMessage, ToolMessage, AIMessage, ToolCall, AnyMessage
from langgraph.config import get_stream_writer
from langgraph.types import StreamWriter
from pydantic import BaseModel, Field
from tavily import AsyncTavilyClient

from nilo_agent.clients.nilo_api.client import NiloApiClient
from nilo_agent.enums import IntentType, ResponseCode
from nilo_agent.exceptions import BusinessException
from nilo_agent.graph.answers import CANNED_ANSWERS
from nilo_agent.graph.model.search_answer import SearchAnswer
from nilo_agent.graph.prompts import build_initial_video_context, build_classifier_input, SELF_INTRO_PROMPT, \
    SEARCH_PROMPT, DISCUSS_PROMPT
from nilo_agent.graph.search_verify import verify_videos, verify_segments, render_search_answer
from nilo_agent.graph.state import AskState
from nilo_agent.graph.tools import get_search_tools, SearchContext, get_discuss_tools
from nilo_agent.metrics import TOOL_CALLS
from nilo_agent.schemas.ask import CitedVideo, CitedSegment

TOOL_ACTIONS = {
    "search_video": "检索视频",
    "get_video_detail": "查看视频详情",
    "search_transcript": "检索字幕",
    "web_search": "联网搜索",
}
"""
Agent 调用工具时，推给前端的步骤名
"""

CONVERSATION_LIFETIME_SEC = 24 * 60 * 60

log = logging.getLogger(__name__)


class AskNodes:
    """
    聊天场景时图中节点
    """

    def __init__(self, nilo_api_client: NiloApiClient, model: BaseChatModel, tavily_client: AsyncTavilyClient):
        self._nilo_api_client = nilo_api_client
        self._model = model
        """
        基础模型
        """
        self._classifier_model = model.with_structured_output(IntentDecision)
        """
        分类器模型，带结构化输出，输出只能是意图之一
        """
        self._search_agent = create_agent(
            model,
            get_search_tools(nilo_api_client),
            system_prompt=SEARCH_PROMPT,
            context_schema=SearchContext,
            response_format=ToolStrategy(SearchAnswer),
            middleware=[ToolCallLimitMiddleware(run_limit=4),
                        ModelCallLimitMiddleware(run_limit=6, exit_behavior="error")],
            checkpointer=False
        )
        self._discuss_agent = create_agent(
            model,
            get_discuss_tools(nilo_api_client, tavily_client),
            system_prompt=DISCUSS_PROMPT,
            context_schema=SearchContext,
            # 每次联网都花 Tavily 额度，单独限次数
            middleware=[ToolCallLimitMiddleware(tool_name="web_search", run_limit=2),
                        ToolCallLimitMiddleware(run_limit=4),
                        ModelCallLimitMiddleware(run_limit=6, exit_behavior="error")],
            checkpointer=False
        )

    async def prepare(self, state: AskState) -> AskState:
        """
        准备阶段

        每轮都检查有没有传入 video_id，如果传入，查出视频标题；video_id 不存在时 Java 返回业务错误
        """
        # 会话从创建起超过 24 小时就不能再提问；每轮都检查有没有传入 video_id，如果传入，查出视频标题
        created_at = state.get("created_at", time.time())  # 新会话没有这个字段，这一轮就是创建时间
        if time.time() - created_at > CONVERSATION_LIFETIME_SEC:
            raise BusinessException(ResponseCode.INVALID_ARGUMENTS.code, "会话已过期，请新建会话")

        video_id = state["video_id"]

        if video_id is None:  # 主页 AI 助手
            video_name = None
        else:  # 视频页 AI 助手
            video_name = await self._nilo_api_client.find_video_name(video_id)

        return {
            "created_at": created_at,
            "video_name": video_name
        }

    async def init_conversation(self, state: AskState) -> AskState:
        """
        初始化会话

        仅在创建视频页新会话前执行该节点，提供视频相关信息，便于初始情况下的意图判断
        """
        files = await self._nilo_api_client.list_summaries(state["video_id"])
        return {
            "messages": [SystemMessage(build_initial_video_context(state["video_id"], state["video_name"], files))]
        }

    async def classify(self, state: AskState) -> AskState:
        """
        意图分类
        """
        # 根据结构化输出，这里返回的肯定是 IntentDecision 类型
        intent_decision = cast(IntentDecision,
                               await self._classifier_model.ainvoke(build_classifier_input(_with_question(state))))
        return {
            "intent": intent_decision.intent
        }

    async def self_intro(self, state: AskState) -> AskState:
        """
        结果1：让模型生成自我介绍
        """
        reply = await self._model.ainvoke([SystemMessage(SELF_INTRO_PROMPT), HumanMessage(state["question"])])
        answer = reply.text.strip()
        if not answer:
            raise RuntimeError("模型没有返回自我介绍")
        return {"answer": answer, "videos": [], "segments": [], "messages": _record_turn(state, answer)}

    async def canned(self, state: AskState) -> dict:
        """
        结果2：模型回复固定话术
        """
        if state["intent"] == IntentType.REJECT:
            log.info(f"请求不在白名单内，已拒绝, question={state["question"]}")
        answer = CANNED_ANSWERS[state["intent"]]
        return {
            "answer": answer,
            "videos": [],
            "segments": [],
            "messages": _record_turn(state, answer)
        }

    async def search_agent(self, state: AskState) -> AskState:
        """
        结果3：搜索 Agent 运行，执行视频内容搜索
        """
        stream_writer = get_stream_writer()
        messages = _with_question(state)

        result = None
        async for mode, payload in self._search_agent.astream(
                {"messages": messages},
                context=SearchContext(state["video_id"]),
                stream_mode=["updates", "values"]
        ):
            if mode == "updates":  # updates：模型每回复一次，把要调用的工具推给前端
                if "model" in payload:
                    _write_tool_steps(stream_writer, payload["model"]["messages"][0])
            else:  # values：每走一步给一次完整状态，最后一次就是 Agent 跑完的结果
                result = payload

        search_answer: SearchAnswer | None = result.get("structured_response")
        if search_answer is None:
            raise RuntimeError("模型没有返回结构化的回答")

        new_messages = result["messages"][len(messages):]

        video_hits: list[CitedVideo] = []
        transcript_hits: list[CitedSegment] = []

        # 挑选出工具返回的引用视频/片段
        for new_message in new_messages:
            if isinstance(new_message, ToolMessage) and new_message.artifact:
                if new_message.name == "search_transcript":
                    transcript_hits.extend(new_message.artifact)
                else:
                    video_hits.extend(new_message.artifact)

        return {
            "draft": search_answer.summary,
            "draft_videos": search_answer.videos,
            "draft_segments": search_answer.segments,
            "video_hits": video_hits,
            "transcript_hits": transcript_hits
        }

    async def search_verify(self, state: AskState) -> AskState:
        """
        核对回答草稿

        只留下检索结果里真有的视频和片段，标题用检索结果里的；核对后的回答写进对话记录，下一轮追问时能看到
        """
        videos = verify_videos(state["draft_videos"], state["video_hits"])
        segments = verify_segments(state["draft_segments"], state["transcript_hits"])
        answer = render_search_answer(state["draft"], videos, segments)
        return {
            "answer": answer,
            "videos": [video for video, _ in videos],
            "segments": [segment for segment, _ in segments],
            "messages": _record_turn(state, answer)
        }

    async def discuss_agent(self, state: AskState) -> AskState:
        """
        结果4：讨论 Agent 运行，围绕视频内容继续聊，需要时查字幕、联网搜索
        """
        stream_writer = get_stream_writer()

        replies: list[AIMessage] = []
        async for mode, payload in self._discuss_agent.astream(
                {"messages": _with_question(state)},
                context=SearchContext(state["video_id"]),
                stream_mode=["messages", "updates"]
        ):
            if mode == "messages":
                chunk, metadata = payload
                # 只转发模型说的话；工具返回的结果也会出现在这里，不转发
                if metadata["langgraph_node"] == "model" and chunk.text:
                    stream_writer({
                        "event": "delta",
                        "text": chunk.text
                    })
            else:  # updates：模型每回复一次，把要调用的工具推给前端，再记下这次回复
                if "model" in payload:
                    reply: AIMessage = payload["model"]["messages"][0]
                    _write_tool_steps(stream_writer, reply)
                    replies.append(reply)

        # 和普通的对话模型一样，最后那段文字就是回答，不做核对
        answer = replies[-1].text.strip()

        if not answer:
            raise RuntimeError("模型没有返回回答")

        return {
            "answer": answer,
            "videos": [],
            "segments": [],
            "messages": _record_turn(state, answer)
        }


class IntentDecision(BaseModel):
    """
    意图识别类
    """

    # 包一层是为了让结构化输出生成 JSON Schema，模型只能填枚举里的值
    intent: IntentType = Field(description="意图识别结果只能从枚举中选择")


def _with_question(state: AskState) -> list[AnyMessage]:
    """
    这一轮给模型看的对话：之前的记录加上这一轮的问题

    问题这时还不写进记录，等这一轮答完和回答一起写，见 _record_turn
    """
    return [*state.get("messages", []), HumanMessage(state["question"])]


def _record_turn(state: AskState, answer: str) -> list[AnyMessage]:
    """
    这一轮的问答，由结果节点写进对话记录

    中途出错时什么都不写，下一轮不会看到一个没有回答的问题，也就不会被它带偏
    """
    return [HumanMessage(state["question"]), AIMessage(answer)]


def _write_tool_steps(stream_writer: StreamWriter, reply: AIMessage) -> None:
    """
    模型这次回复要调用工具时，每次调用给前端推一步，比如「联网搜索：Navicat 收费」

    结构化输出也是借工具调用实现的，不在 TOOL_ACTIONS 里的工具不推，也不计入工具调用次数
    """
    for tool_call in reply.tool_calls:
        action = TOOL_ACTIONS.get(tool_call["name"])
        if action is not None:
            TOOL_CALLS.labels(tool_call["name"]).inc()
            stream_writer({
                "event": "status",
                "text": _build_tool_step(action, tool_call)
            })


def _build_tool_step(action: str, tool_call: ToolCall) -> str:
    """
    有检索词的写成「动作：检索词」，没有的只写动作（查看视频详情只带 video_id，给用户看没意义）
    """
    query = tool_call["args"].get("query")
    if query:
        return f"{action}：{query}"
    else:
        return action
