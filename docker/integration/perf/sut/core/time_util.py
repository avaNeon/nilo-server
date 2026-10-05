"""时间格式化。时间戳都是带小数的秒数（time.time() 的返回值）"""
# 标准库
import datetime


def iso_time(t: float) -> str:
    """时间戳转成 UTC 时间字符串，精确到毫秒，如 2026-10-04T08:00:00.123+00:00"""
    return datetime.datetime.fromtimestamp(t, datetime.timezone.utc).isoformat(timespec='milliseconds')


def utc_date(t: float) -> str:
    """时间戳对应的 UTC 日期，如 2026-10-04。服务以 -Duser.timezone=UTC 运行，当日播放量哈希的 key 按 UTC 日期拼接"""
    return datetime.datetime.fromtimestamp(t, datetime.timezone.utc).date().isoformat()
