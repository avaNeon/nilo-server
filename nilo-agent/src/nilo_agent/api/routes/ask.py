from collections.abc import AsyncIterable
from typing import Annotated

from fastapi import APIRouter, Query
from fastapi.params import Header
from fastapi.sse import ServerSentEvent, EventSourceResponse
from pydantic import StringConstraints

from nilo_agent.api.deps import AskServiceDep
from nilo_agent.api.sse import handle_exceptions

router = APIRouter(prefix="/ai", tags=["ask"])


@router.post("/ask", response_class=EventSourceResponse)
async def ask(service: AskServiceDep,
              token: Annotated[str, Header()],
              question: Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=100), Query()],
              conversation_id: Annotated[
                  str,
                  StringConstraints(strip_whitespace=True, min_length=1),
                  Query(alias="conversationId", pattern=r"^[A-Za-z0-9-]{1,64}$")],
              video_id: Annotated[int | None, Query(alias="videoId")] = None) -> AsyncIterable[ServerSentEvent]:
    async for event in handle_exceptions(service.ask(token, question, conversation_id, video_id)):
        yield event


