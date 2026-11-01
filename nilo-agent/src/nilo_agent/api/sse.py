import logging
from collections.abc import AsyncIterable

from fastapi.sse import ServerSentEvent

from nilo_agent.exceptions import BusinessException
from nilo_agent.schemas.ask import TextData

ERROR_TEXT = '服务器内部错误'

log = logging.getLogger(__name__)


async def handle_exceptions(events: AsyncIterable[ServerSentEvent]) -> AsyncIterable[ServerSentEvent]:
    """
    处理流式接口异常

    :param events: 生成器对象
    :return: 处理后的生成器对象
    """
    try:
        async for event in events:
            yield event
    except BusinessException as e:
        yield ServerSentEvent(event="error", data=TextData(text=e.message))
    except Exception:
        log.exception("流式接口出错")
        yield ServerSentEvent(event="error", data=TextData(text=ERROR_TEXT))
