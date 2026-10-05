"""LoadReport：压测机调用 /load-finished 时，在请求体里发来的 k6 简要结果，由 load/load.js 的 handleSummary 生成。
相当于 Java 里带 @RequestBody、@Valid 的 DTO：FastAPI 按这里声明的字段和类型解析请求体，不符合就自动返回 422。
"""
# 第三方库
from pydantic import BaseModel, ConfigDict


class LoadReport(BaseModel):
    """k6 简要结果。对账只用到下面声明的两个字段；其余字段（请求数、响应时间等）原样保留，写进结果留档"""
    model_config = ConfigDict(extra='allow')  # 允许并保留没有声明的字段

    end_ms: int  # 压测结束时刻（压测机的时间，毫秒）：k6 收到最后一个响应的时刻
    accepted: int  # 放行请求数：接口返回成功的请求数，对账以它为准
