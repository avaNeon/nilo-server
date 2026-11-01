from typing import Annotated

from fastapi import Request, Depends

from nilo_agent.services.ask import AskService


def get_ask_service(request: Request):
    return request.app.state.ask_service


AskServiceDep = Annotated[AskService, Depends(get_ask_service)]
