import logging

from nilo_agent.graph.model.search_answer import AnswerSegment, AnswerVideo
from nilo_agent.schemas.ask import CitedSegment, CitedVideo

NOT_FOUND_NOTE = "（未找到合法的视频）"

log = logging.getLogger(__name__)


def verify_videos(picks: list[AnswerVideo], hits: list[CitedVideo]) -> list[tuple[CitedVideo, str]]:
    """
    核对模型给出的视频

    只留下检索结果里有的，按 video_id 去重；标题用检索结果里的
    :param picks: 模型给出的视频
    :param hits: 本轮视频检索和详情查询的结果
    :return: 核对通过的视频，和模型写的理由
    """
    hit_by_id = {hit.video_id: hit for hit in hits}
    cited: dict[str, tuple[CitedVideo, str]] = {}

    for pick in picks:
        hit = hit_by_id.get(pick.video_id)
        if hit is None:
            log.info(f"回答里的视频不在检索结果里，已删掉, video_id={pick.video_id}")
            continue
        # 防止重复引用，相同视频只取第一次回答
        cited.setdefault(pick.video_id, (hit, pick.reason))

    return list(cited.values())


def verify_segments(picks: list[AnswerSegment], hits: list[CitedSegment]) -> list[tuple[CitedSegment, str]]:
    """
    核对模型给出的字幕片段

    同一视频、同一分P里要有一条字幕命中；按视频、分P、时间去重
    :param picks: 模型给出的片段
    :param hits: 本轮字幕检索的命中
    :return: 核对通过的片段，和模型写的转述
    """
    hit_by_key = {(hit.video_id, hit.file_index, hit.start_sec): hit for hit in hits}
    cited: dict[tuple[str, int, int], tuple[CitedSegment, str]] = {}

    for pick in picks:
        key = (pick.video_id, pick.file_index, pick.start_sec)
        hit = hit_by_key.get(key)
        if hit is None:
            log.info(
                f"回答片段对不上字幕命中，已删除, video_id={pick.video_id},file_index={pick.file_index}, start_sec={pick.start_sec}")
            continue
        segment = CitedSegment(video_id=hit.video_id, video_name=hit.video_name,
                               file_index=pick.file_index, start_sec=pick.start_sec)
        cited.setdefault(key, (segment, pick.reason))

    return list(cited.values())


def render_search_answer(draft: str | None, videos: list[tuple[CitedVideo, str]],
                         segments: list[tuple[CitedSegment, str]]) -> str:
    """
    拼出给用户看的回答

    首行是模型的总结，之后每个视频、每个片段各占一行；两个都为空时最后补一句提示，不覆盖模型的总结，
    """
    lines: list[str] = [draft] if draft else []

    for video, reason in videos:
        lines.append(f"《{video.video_name}》({video.video_id}): {reason}")

    for segment, reason in segments:
        lines.append(f"《{segment.video_name}》({segment.video_id})"
                     f"[P{segment.file_index} {_format_time(segment.start_sec)}]:{reason}")

    if not videos and not segments:
        lines.append(NOT_FOUND_NOTE)

    return "\n".join(lines)


def _format_time(seconds: int) -> str:
    """
    秒数转成 m:ss，超过一小时转成 h:mm:ss，和字幕行首的写法一致
    """
    hours, rest = divmod(seconds, 3600)
    minutes, secs = divmod(rest, 60)
    if hours > 0:
        return f"{hours}:{minutes:02d}:{secs:02d}"
    else:
        return f"{minutes}:{secs:02d}"
