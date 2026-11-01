from contextlib import asynccontextmanager

import httpx
from fastapi import FastAPI
from langchain_deepseek import ChatDeepSeek
from langgraph.checkpoint.redis import AsyncShallowRedisSaver
from tavily import AsyncTavilyClient

from nilo_agent.clients.nilo_api.client import NiloApiClient
from nilo_agent.config import get_settings
from nilo_agent.services.ask import AskService

CONVERSATION_TTL_MINUTES = 24 * 60


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = get_settings()
    model = ChatDeepSeek(
        model="deepseek-flash",
        api_key=settings.llm_api_key,
        timeout=90,
        extra_body={"thinking": {"type": "disabled"}}
    )
    async with (httpx.AsyncClient(base_url=settings.java_api_base_url,
                                  headers={"X-Internal-Token": settings.internal_token},
                                  timeout=10) as nilo_api_http,
                AsyncTavilyClient(api_key=settings.tavily_api_key) as tavily_client,
                AsyncShallowRedisSaver.from_conn_string(
                    settings.redis_url,
                    ttl={"default_ttl": CONVERSATION_TTL_MINUTES, "refresh_on_read": False}
                ) as checkpointer):
        await checkpointer.asetup()  # 在 Redis 里建索引，已经建过就跳过
        app.state.nilo_api_client = NiloApiClient(nilo_api_http)
        app.state.ask_service = AskService(app.state.nilo_api_client, model, tavily_client, checkpointer)
        yield
