import httpx
from pydantic import TypeAdapter

from nilo_agent.clients.nilo_api.models import ApiResponse, VideoFileSummary, VideoHit, VideoDetail, TranscriptHit
from nilo_agent.enums import ResponseCode
from nilo_agent.exceptions import BusinessException


class NiloApiClient:
    def __init__(self, http: httpx.AsyncClient):
        self._http = http

    async def consume_quota(self, token: str) -> str:
        """
        消耗额度
        :param token: 用户 token
        :return: redis key
        """
        resp = await self._http.post("/inner/ask-quota/consume", headers={"token": token})
        return _unwrap(resp, str)

    async def refund_quota(self, quota_key: str) -> None:
        """
        返还额度
        :param quota_key: redis key
        """
        resp = await self._http.post("/inner/ask-quota/refund", params={"quotaKey": quota_key})
        # 检查一下调用是否成功即可，成功时 data 为 null
        _unwrap(resp, type(None))

    async def find_video_name(self, video_id: int) -> str:
        """
        查询视频标题

        视频页每轮提问都用它确认 video_id 有效
        :param video_id: 视频 id
        :return: 视频标题。查不到时 Java 返回业务错误，这里抛 BusinessException
        """
        resp = await self._http.get(f"/inner/video/{video_id}")
        return _unwrap(resp, str)

    async def list_summaries(self, video_id: int) -> list[VideoFileSummary]:
        """
        查视频各分P的总结

        用于视频页新会话初始化
        :param video_id: 视频 id
        :return: 前几个分P的总结，按分P顺序排好；没有总结的分P不在里面
        """
        resp = await self._http.get(f"/inner/video/{video_id}/summaries")
        return _unwrap(resp, list[VideoFileSummary])

    async def search_video(self, query: str) -> list[VideoHit]:
        """
        按语义检索视频
        :param query: 检索词
        :return: 最相关的最多 5 个视频
        """
        resp = await self._http.get("/inner/video-tool/search-video", params={"query": query})
        return _unwrap(resp, list[VideoHit])

    async def get_video_detail(self, video_id: int) -> VideoDetail | None:
        """
        查询视频详情
        :param video_id: 视频 id
        :return: 视频详情；查不到时为 None
        """
        resp = await self._http.get(f"/inner/video-tool/video-detail/{video_id}")
        return _unwrap(resp, VideoDetail | None)

    async def search_transcript(self, query: str, video_id: int | None) -> list[TranscriptHit]:
        """
        按语义检索字幕
        :param query: 检索词
        :param video_id: 只搜这个视频；为 None 时搜全站
        :return: 最相关的最多 5 个字幕片段
        """
        params: dict[str, str | int] = {"query": query}
        if video_id is not None:  # 不传 videoId 就是搜全站
            params["videoId"] = video_id
        resp = await self._http.get("/inner/video-tool/search-transcript", params=params)
        return _unwrap(resp, list[TranscriptHit])


def _unwrap[T](resp: httpx.Response, data_type: type[T]) -> T:
    """
    解析 API 响应

    若 响应状态码/内部状态码 不为 200，抛出异常；或者响应体中 status 不为 success，则抛出 BusinessException 异常。
    解析成功后返回响应体中的 data 字段，并将其转换为指定的数据类型。
    :param data_type: data 数据类型
    :return: 转化为指定类型的 data
    """
    resp.raise_for_status()
    body: ApiResponse = ApiResponse.model_validate_json(resp.content)
    if body.status != "success":
        # Java 全局异常处理器把服务端错误也包成了 200 + status=error，只能按码区分，服务端错误要走退额度
        if body.code == ResponseCode.SERVER_ERROR.code:
            raise RuntimeError(f"Java 服务内部错误: {body.info}")
        else:
            raise BusinessException(body.code, body.info if body.info else "未知异常")
    # 将 data 校验并转换为指定类型，然后返回
    return TypeAdapter(data_type).validate_python(body.data)
