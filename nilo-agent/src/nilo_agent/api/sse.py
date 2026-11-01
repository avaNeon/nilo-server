import logging
from collections.abc import AsyncIterable

from fastapi.sse import ServerSentEvent

from nilo_agent.exceptions import BusinessException
from nilo_agent.metrics import ASK_FAILURES
from nilo_agent.schemas.ask import TextData

ERROR_TEXT = '服务器内部错误'

log = logging.getLogger(__name__)


async def handle_exceptions(events: AsyncIterable[ServerSentEvent]) -> AsyncIterable[ServerSentEvent]:
    """
    处理流式接口异常

    目前流式接口只有提问，没答完的提问次数记在这里：扣额度时的未登录、额度用完也会走到这里
    :param events: 生成器对象
    :return: 处理后的生成器对象
    """
    try:
        async for event in events:
            yield event
    except BusinessException as e:
        ASK_FAILURES.labels("business").inc()
        yield ServerSentEvent(event="error", data=TextData(text=e.message))
    except Exception:
        ASK_FAILURES.labels("error").inc()
        log.exception("流式接口出错")
        yield ServerSentEvent(event="error", data=TextData(text=ERROR_TEXT))
