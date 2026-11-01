from pydantic import BaseModel, Field


class AnswerVideo(BaseModel):
    """
    回答涉及的一个视频
    """
    video_id: str = Field(description="照抄 search_video 或 get_video_detail 返回的 video_id")
    reason: str = Field(
        description="用提问的语言写一句话：找视频时写为什么符合，问详情时写查到的数字；只能依据工具返回的内容")


class AnswerSegment(BaseModel):
    """
    和问题相关的一个字幕片段
    """
    video_id: str = Field(description="照抄 search_transcript 返回的 video_id")
    file_index: int = Field(description="照抄 search_transcript 返回的 file_index")
    start_sec: int = Field(description="照抄 search_transcript 返回的 start_sec")
    reason: str = Field(description="用提问的语言转述这段字幕讲了什么，只能依据字幕原文")


class SearchAnswer(BaseModel):
    """
    最终回答。视频和片段只能来自本轮工具返回的结果，没有就留空
    """
    summary: str = Field(description="用提问的语言，一两句话回答用户的问题；没找到就说没找到")
    videos: list[AnswerVideo] = Field(
        description="回答涉及的视频：找视频时是符合的结果，问时长、播放量时是被问的那个视频；最多 5 个")
    segments: list[AnswerSegment] = Field(
        description="相关的字幕片段：用户问某个内容在视频的哪一段、讲了什么时填，按时间先后排列，最多 3 个")
