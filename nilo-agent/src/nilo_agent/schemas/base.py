from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel


class CamelModel(BaseModel):
    """
    Pydantic 模型基类，使用驼峰命名法作为字段别名
    """
    # FastAPI 推 SSE 时不带参数调用 model_dump_json，要在这里指定按别名输出，前端才能拿到 videoId 这样的字段
    model_config = ConfigDict(alias_generator=to_camel, validate_by_name=True, serialize_by_alias=True)
