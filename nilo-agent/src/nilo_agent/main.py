import logging

import uvicorn
from dotenv import load_dotenv
from fastapi import FastAPI, Response
from prometheus_client import generate_latest, CONTENT_TYPE_LATEST

from nilo_agent.api.exception_handlers import register_exception_handlers
from nilo_agent.api.routes import ask
from nilo_agent.config import get_settings
from nilo_agent.lifespan import lifespan

load_dotenv()

# 和 Java 服务一样，不开放接口文档页（/docs、/redoc、/openapi.json）
app = FastAPI(title="nilo-agent", lifespan=lifespan, docs_url=None, redoc_url=None, openapi_url=None)
app.include_router(ask.router)
register_exception_handlers(app)


def run() -> None:
    """
    启动服务，`uv run nilo-agent` 会调用这里
    """
    # 不配的话只输出 WARNING 以上，代码里的 log.info 都看不到
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s - %(message)s")
    settings = get_settings()
    uvicorn.run(app, host=settings.host, port=settings.port)


@app.get("/metrics", include_in_schema=False)
async def metrics() -> Response:
    """
    给 Prometheus 抓取的指标
    """
    return Response(generate_latest(), media_type=CONTENT_TYPE_LATEST)
