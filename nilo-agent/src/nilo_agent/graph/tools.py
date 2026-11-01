import logging
from dataclasses import dataclass
from typing import Annotated

import httpx
from langchain_core.tools import BaseTool, tool, ToolException
from langgraph.prebuilt import ToolRuntime
from pydantic_core import to_json
from tavily import AsyncTavilyClient
from tavily.errors import ForbiddenError, TimeoutError as TavilyTimeoutError, UsageLimitExceededError

from nilo_agent.clients.nilo_api.client import NiloApiClient
from nilo_agent.clients.nilo_api.models import VideoHit, VideoDetail
from nilo_agent.schemas.ask import CitedVideo, CitedSegment

_JAVA_LONG_MAX = 2 ** 63 - 1

log = logging.getLogger(__name__)


@dataclass
class SearchContext:
    scope_video_id: int | None  # 自动注入当前视频ID，防止模型填错


def get_search_tools(nilo_api_client: NiloApiClient) -> list[BaseTool]:
    """
    获取搜索 Agent 的工具：检索视频、查视频详情、检索字幕
    """
    return _handle_tool_errors([
        _get_search_video_tool(nilo_api_client),
        _get_video_detail_tool(nilo_api_client),
        _get_search_transcript_tool(nilo_api_client),
    ])


def get_discuss_tools(nilo_api_client: NiloApiClient, tavily_client: AsyncTavilyClient) -> list[BaseTool]:
    """
    获取讨论 Agent 的工具：检索字幕、联网搜索
    """
    return _handle_tool_errors([
        _get_search_transcript_tool(nilo_api_client),
        _get_web_search_tool(tavily_client),
    ])


def _handle_tool_errors(tools: list[BaseTool]) -> list[BaseTool]:
    """
    只接住 ToolException，把错误信息当工具结果交给模型重试；其他异常照常抛出，整个请求失败、退额度
    """
    for agent_tool in tools:
        agent_tool.handle_tool_error = True
    return tools


def _get_search_video_tool(nilo_api_client: NiloApiClient) -> BaseTool:
    """
    创建检索站内视频的工具
    """

    @tool(response_format="content_and_artifact")
    async def search_video(
            query: Annotated[str, "检索词。用你从用户问题里提炼出的主题，不必原样照抄整句话"]
    ) -> tuple[str, list[CitedVideo]]:
        """
        在 Nilo 站内按语义检索视频，返回最相关的最多 5 条，每条包含 video_id、标题和检索原文（标题、标签、简介）。
        检索按意思匹配，同义说法也能命中，比如"垃圾回收"能找到讲 GC 的视频。
        """
        log.info(f"工具调用 search_video, query={query}")
        hits: list[VideoHit] = await nilo_api_client.search_video(query)
        cites = [CitedVideo(video_id=hit.video_id, video_name=hit.video_name) for hit in hits]
        return to_json(hits, by_alias=False).decode(), cites

    return search_video


def _get_video_detail_tool(nilo_api_client: NiloApiClient) -> BaseTool:
    """
    创建查询视频详情的工具
    """

    @tool(response_format="content_and_artifact")
    async def get_video_detail(
            video_id: Annotated[str, "视频 id，取自 search_video 的返回结果或之前的对话"],
    ) -> tuple[str, list[CitedVideo]]:
        """
        按 video_id 查询视频详情，包括时长 duration（单位：秒）、播放量 play_count、弹幕数 danmaku_count、收藏数 collect_count。
        只能传 search_video 返回过、或者之前对话里出现过的 video_id。站内查不到时返回 null。
        """
        log.info(f"工具调用 get_video_detail, video_id={video_id}")
        detail: VideoDetail | None = await nilo_api_client.get_video_detail(_parse_video_id(video_id))
        if detail is None:
            cited = []
        else:  # 追问「第二个多长」时这一轮不会检索，这里也记一笔，最后返回的 videos 才不是空的
            cited = [CitedVideo(video_id=detail.video_id, video_name=detail.video_name)]
        return to_json(detail, by_alias=False).decode(), cited

    return get_video_detail


def _get_search_transcript_tool(nilo_api_client: NiloApiClient) -> BaseTool:
    """
    创建检索字幕的工具
    """

    @tool(response_format="content_and_artifact")
    async def search_transcript(
            runtime: ToolRuntime[SearchContext],
            query: Annotated[
                str,
                "检索词。写成字幕的语言，英文视频用英文关键词；不确定视频是什么语言时，中英文关键词写在一起"
            ],
            video_id: Annotated[
                str | None,
                "只在这个视频里搜，取自 search_video 的返回结果或之前的对话；不传就搜全站。"
                "用户在视频页提问时系统会自动限定为当前视频"
            ] = None,
    ) -> tuple[str, list[CitedSegment]]:
        """
        按语义检索视频字幕，返回最相关的最多 5 个字幕片段。每个片段包含 video_id、标题、第几P（file_index）、起止秒数，
        以及这段的字幕原文（一条一行，行首方括号里是这条字幕出现的时间）。
        用户问视频里讲了什么、从哪里开始讲、在第几分钟、在哪一段时必须调用，不能凭记忆或上一轮的时间点回答。
        返回的字幕原文是回答的唯一依据，原文没写的内容不要补充。
        字幕是视频原声的语言，英文视频就是英文字幕；检索词用字幕的语言，回答用户时再用用户的语言转述。
        """
        scope_video_id = runtime.context.scope_video_id
        log.info(f"工具调用 search_transcript, query={query}, video_id={video_id}, scope_video_id={scope_video_id}")
        if scope_video_id is not None:  # 视频页：强制只搜当前视频，模型传什么都不管用
            search_video_id = scope_video_id
        elif video_id:
            search_video_id = _parse_video_id(video_id)
        else:  # 主页且没传：搜全站
            search_video_id = None
        hits = await nilo_api_client.search_transcript(query, search_video_id)

        cites = [CitedSegment(video_id=hit.video_id, video_name=hit.video_name,
                              file_index=hit.file_index, start_sec=hit.start_sec) for hit in hits]

        return to_json(hits, by_alias=False).decode(), cites

    return search_transcript


def _get_web_search_tool(tavily_client: AsyncTavilyClient) -> BaseTool:
    """
    创建联网搜索的工具
    """

    @tool()
    async def web_search(
            query: Annotated[str, "检索词。提炼成搜索引擎用的关键词，不要原样照抄整句话"]
    ) -> str:
        """
        在互联网上搜索，返回最相关的最多 10 个网页，每个网页包含标题 title、链接 url 和正文摘录 content。
        用来查视频以外的信息，比如某个软件现在收不收费、视频里的说法现在还对不对。视频里讲了什么用 search_transcript 查。
        """
        log.info(f"工具调用 web_search, query={query}")

        # 联网只是补充：限流、额度用完、超时、断网、对方 5xx 时交给模型，让它只凭视频内容回答。
        # 额度用完时 Tavily 回 432/433，SDK 抛的是 ForbiddenError；key 配错这类问题照常抛出，整个请求失败、退额度
        try:
            response = await tavily_client.search(query, max_results=10, timeout=10)  # SDK 默认要等 60 秒
        except (UsageLimitExceededError, ForbiddenError, TavilyTimeoutError, httpx.HTTPError) as e:
            log.warning(f"网络搜索失败, query={query}, error={e!r}")
            raise ToolException(
                "网络搜索暂时不可用，不要再调用 web_search。只根据视频内容回答，并告诉用户这次没能联网查证") from e

        results = response["results"]
        # 只给模型标题、链接和摘录，score 这些字段用不上
        pages = [{"title": result["title"], "url": result["url"], "content": result["content"]} for result in results]

        return to_json(pages).decode()

    return web_search


def _parse_video_id(video_id: str) -> int:
    """
    把模型传来的 video_id 转成 int

    不是纯数字、或超出 Java Long 范围时抛 ToolException，让模型换个正确的 id 重试。
    转成 int 再拼进 URL，模型也就没法借 video_id 改请求路径
    """
    video_id = video_id.strip()
    if not video_id.isdecimal() or int(video_id) > _JAVA_LONG_MAX:
        raise ToolException(f"video_id 必须是纯数字，取自 search_video 的返回结果，收到的是：{video_id}")
    return int(video_id)
