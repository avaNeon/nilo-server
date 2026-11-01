from typing import Any

from nilo_agent.schemas.base import CamelModel


class ApiResponse(CamelModel):
    """
    API 响应对象。错误需要原样推送给前端。
    """
    status: str
    code: int
    info: str | None = None
    data: Any = None


class VideoChapter(CamelModel):
    """
    视频里的一个章节
    """
    start_sec: int
    title: str


class VideoFileSummary(CamelModel):
    """
    一个分P的 AI 总结，章节时间从这一P开头算起
    """
    file_index: int
    summary: str
    # summary.json 里可能没有章节，Java 会传 null
    chapters: list[VideoChapter] | None = None


class VideoHit(CamelModel):
    """
    视频检索命中的一个视频
    """
    video_id: str
    video_name: str
    text: str  # 检索原文（标题、标签、简介），Java 已截到 400 字


class VideoDetail(CamelModel):
    """
    视频详情，只取给模型看的字段，其余字段解析时忽略
    """
    video_id: str
    video_name: str
    duration: int  # 单位：秒
    play_count: int
    danmaku_count: int
    collect_count: int


class TranscriptHit(CamelModel):
    """
    字幕检索命中的一个片段，时间都从这一P开头算起
    """
    video_id: str
    video_name: str
    file_index: int
    start_sec: int  # lines 第一行的时间，含前文
    end_sec: int
    lines: str  # 字幕原文，一条一行，行首方括号里是这条字幕的时间
