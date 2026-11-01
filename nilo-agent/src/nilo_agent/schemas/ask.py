from typing import Annotated

from pydantic import StringConstraints

from nilo_agent.schemas.base import CamelModel

NonBlank = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1)]
"""
非空字符串约束
"""


class TextData(CamelModel):
    """
    回复文本信息
    """
    text: str


class CitedVideo(CamelModel):
    """
    回复时引用的视频信息
    """
    video_id: str
    video_name: str


class CitedSegment(CamelModel):
    """
    回复时引用的视频片段（时间点）信息
    """
    video_id: str
    video_name: str
    file_index: int
    start_sec: int


class AskDoneData(CamelModel):
    """
    一轮问答结束时的完整结果
    """
    answer: str
    videos: list[CitedVideo]
    segments: list[CitedSegment]
