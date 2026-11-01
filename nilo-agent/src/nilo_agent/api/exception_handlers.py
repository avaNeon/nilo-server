import logging
from typing import Any

from fastapi import Request, FastAPI
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from nilo_agent.enums import ResponseCode
from nilo_agent.exceptions import BusinessException

log = logging.getLogger(__name__)


def register_exception_handlers(app: FastAPI):
    @app.exception_handler(BusinessException)
    async def business_exception_handler(request: Request, exception: BusinessException) -> JSONResponse:
        log.info(f"业务异常：{exception.message}")
        return _error(exception.code, exception.message)

    @app.exception_handler(RequestValidationError)
    async def validation_exception_handler(request: Request, exception: RequestValidationError) -> JSONResponse:
        log.info(f"参数不合法：{exception}")
        return _error(ResponseCode.INVALID_ARGUMENTS.code,
                      ResponseCode.INVALID_ARGUMENTS.message,
                      [e["message"] for e in exception.errors()])

    @app.exception_handler(Exception)
    async def unhandled_exception_handler(request: Request, exception: Exception) -> JSONResponse:
        log.exception(f"未知异常：{exception}")
        return _error(ResponseCode.UNKNOWN_ERROR.code, ResponseCode.UNKNOWN_ERROR.message)


def _error(code: int, info: str, data: Any = None) -> JSONResponse:
    return JSONResponse({
        "status": "error",
        "code": code,
        "info": info,
        "data": data
    })
