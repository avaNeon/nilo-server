import time
from collections.abc import AsyncIterable
from contextlib import suppress

from fastapi.sse import ServerSentEvent
from langchain_core.language_models import BaseChatModel
from langchain_core.runnables.config import RunnableConfig
from langgraph.checkpoint.base import BaseCheckpointSaver
from tavily import AsyncTavilyClient

from nilo_agent.clients.nilo_api.client import NiloApiClient
from nilo_agent.exceptions import BusinessException
from nilo_agent.graph.builder import build_graph
from nilo_agent.graph.nodes import AskNodes
from nilo_agent.metrics import ASKS, ASK_SECONDS
from nilo_agent.schemas.ask import TextData, AskDoneData

LOOKING_STATUS = "理解你的问题"


class AskService:
    def __init__(self,
                 nilo_api_client: NiloApiClient,
                 model: BaseChatModel,
                 tavily_client: AsyncTavilyClient,
                 checkpoint: BaseCheckpointSaver):
        self._nilo_api_client = nilo_api_client
        self._graph = build_graph(
            AskNodes(nilo_api_client, model, tavily_client),
            checkpoint
        )

    async def ask(self, token: str, question: str, conversation_id: str, video_id: int | None) -> AsyncIterable[
        ServerSentEvent]:
        """
        跑一轮问答，把进度、正文和最终结果推成 SSE

        额度先扣，服务端出错时退回；浏览器中途断开不退，否则拿到正文后断开就能白用
        """
        start = time.perf_counter()
        # 先校验并消耗额度
        quota_key = await self._nilo_api_client.consume_quota(token)

        try:
            yield ServerSentEvent(event="status", data=TextData(text=LOOKING_STATUS))

            final_state = None
            async for mode, payload in self._graph.astream(
                    {"video_id": video_id, "question": question},
                    RunnableConfig(configurable={"thread_id": conversation_id}),
                    stream_mode=["custom", "values"]
            ):
                if mode == "custom":  # 节点推的步骤和正文
                    yield ServerSentEvent(event=payload["event"], data=TextData(text=payload["text"]))
                else:  # values：每走一步给一次完整状态，最后一次就是这一轮的结果
                    final_state = payload
        except BusinessException:
            raise
        except Exception:
            # 包住退款调用，即使退款失败也返回原始错误
            with suppress(Exception):
                await self._nilo_api_client.refund_quota(quota_key)
            raise

        # 答完才记次数和耗时；没答完的在 handle_exceptions 里记
        intent = final_state["intent"]
        ASKS.labels(intent).inc()
        ASK_SECONDS.labels(intent).observe(time.perf_counter() - start)

        # 只有讨论的正文是边生成边推的；其他回答前端从这里取正文
        yield ServerSentEvent(event="done", data=AskDoneData(answer=final_state["answer"],
                                                             videos=final_state["videos"],
                                                             segments=final_state["segments"]))
