"""WriteStopResult：WriteStopService.wait_until_stopped() 的返回值，交给 MeasurementService.reconcile() 对账"""
# 标准库
from dataclasses import dataclass


@dataclass
class WriteStopResult:
    """判定写入停止的结果。@dataclass 会按下面的字段自动生成构造方法，相当于 Java 的 record"""
    stop_time: float  # 写入停止时刻：最后一次写入之后的第一次查询的时间
    mysql_sum: int  # 停止时 MySQL 的播放量总和
    hincrby_calls: int  # 停止时 Redis 的 HINCRBY 累计次数
    timed_out: bool  # 压测结束 30 分钟后仍没有停止
    # 监控本身的开销，用来确认 0.1 秒的查询间隔跟得上
    polls: int  # 查询次数
    avg_poll_ms: float  # 单次查询的平均耗时（毫秒）
    max_poll_ms: float  # 单次查询的最长耗时（毫秒）
