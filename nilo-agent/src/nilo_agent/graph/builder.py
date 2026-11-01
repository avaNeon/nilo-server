from typing import Literal

from langgraph.checkpoint.base import BaseCheckpointSaver
from langgraph.constants import START, END
from langgraph.graph.state import CompiledStateGraph, StateGraph

from nilo_agent.enums import IntentType
from nilo_agent.graph.nodes import AskNodes
from nilo_agent.graph.state import AskState


def build_graph(nodes: AskNodes, checkpointer: BaseCheckpointSaver) -> CompiledStateGraph:
    """
    构造图
    """
    builder = StateGraph(AskState)

    builder.add_node("prepare", nodes.prepare)
    builder.add_node("init_conversation", nodes.init_conversation)
    builder.add_node("classify", nodes.classify)
    builder.add_node("self_intro", nodes.self_intro)
    builder.add_node("canned", nodes.canned)
    builder.add_node("search_agent", nodes.search_agent)
    builder.add_node("search_verify", nodes.search_verify)
    builder.add_node("discuss_agent", nodes.discuss_agent)

    # 视频页的新会话先放一条介绍视频的系统消息，再分类
    builder.add_edge(START, "prepare")
    builder.add_conditional_edges("prepare", _route_after_prepare)
    builder.add_edge("init_conversation", "classify")

    # 按意图分到四种结果；结果节点答完才把这一轮的问答写进对话记录
    builder.add_conditional_edges("classify", _route_by_intent)
    builder.add_edge("self_intro", END)
    builder.add_edge("canned", END)
    # 搜索先出草稿、再核对；讨论的回答直接结束
    builder.add_edge("search_agent", "search_verify")
    builder.add_edge("search_verify", END)
    builder.add_edge("discuss_agent", END)

    return builder.compile(checkpointer=checkpointer)


def _route_after_prepare(state: AskState) -> Literal["init_conversation", "classify"]:
    """
    判断是否需要初始化
    """

    # 如果是视频页且还没有对话记录，说明是新会话，先初始化
    if state["video_id"] is not None and not state.get("messages"):
        return "init_conversation"
    else:
        return "classify"


def _route_by_intent(state: AskState) -> Literal["self_intro", "canned", "search_agent", "discuss_agent"]:
    """
    按意图选结果节点
    """
    intent = state["intent"]
    if intent == IntentType.SELF_INTRO:
        return "self_intro"
    elif intent == IntentType.VIDEO_SEARCH:
        return "search_agent"
    elif intent == IntentType.VIDEO_DISCUSS:
        return "discuss_agent"
    else:  # 站点介绍、投稿、账号、视频总结、拒答，都回固定话术
        return "canned"
