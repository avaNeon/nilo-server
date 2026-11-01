from typing import TypedDict, Annotated

from langchain_core.messages import AnyMessage
from langgraph.graph import add_messages

from nilo_agent.enums import IntentType
from nilo_agent.graph.model.search_answer import AnswerVideo, AnswerSegment
from nilo_agent.schemas.ask import CitedVideo, CitedSegment


class AskState(TypedDict, total=False):
    created_at: float  # 记录会话创建时间
    video_id: int | None  # 视频ID，用于区分视频页聊天和主页聊天
    video_name: str | None  # 视频页提问时的视频标题，每轮重新查一次；首页提问时为 None
    question: str
    intent: IntentType | None
    # 对话记录；视频页的会话第一条是带各分P总结的 SystemMessage，之后每轮答完追加一问一答
    messages: Annotated[list[AnyMessage], add_messages]
    # 搜索 Agent 的回答草稿，verify 核对后拼成 answer
    draft: str | None
    draft_videos: list[AnswerVideo]
    draft_segments: list[AnswerSegment]
    # 通过搜索工具获得的结果
    video_hits: list[CitedVideo]
    transcript_hits: list[CitedSegment]
    # 终点回复的字段
    answer: str | None
    videos: list[CitedVideo]
    segments: list[CitedSegment]
