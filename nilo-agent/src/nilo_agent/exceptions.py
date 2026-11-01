class BusinessException(Exception):
    """
    业务异常类
    """

    def __init__(self, code: int, message: str):
        super().__init__(message)
        self.message = message
        self.code = code
