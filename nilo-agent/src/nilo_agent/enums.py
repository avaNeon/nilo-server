from enum import Enum, StrEnum


class ResponseCode(Enum):
    """
    响应编码
    """
    INVALID_ARGUMENTS = 402, "请求参数不合法"
    SERVER_ERROR = 500, "服务器返回错误，请联系管理员"
    UNKNOWN_ERROR = 1000, "未知错误"

    def __init__(self, code: int, message: str):
        self.code = code
        self.message = message


class IntentType(StrEnum):
    """
    Agent 对话意图类型
    """
    SELF_INTRO = "SELF_INTRO"
    SITE_INTRO = "SITE_INTRO"
    UPLOAD_HELP = "UPLOAD_HELP"
    ACCOUNT_HELP = "ACCOUNT_HELP"
    VIDEO_SEARCH = "VIDEO_SEARCH"
    VIDEO_SUMMARY = "VIDEO_SUMMARY"
    VIDEO_DISCUSS = "VIDEO_DISCUSS"
    REJECT = "REJECT"
