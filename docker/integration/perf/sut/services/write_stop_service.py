"""WriteStopService：判定播放量写入何时停止。serve.py 的主线程在记好起点后调用 wait_until_stopped()。

判定规则：
  每 0.1 秒查询两个值：MySQL 的 SUM(play_count)，Redis 的 HINCRBY 累计执行次数（INFO commandstats）。
  HINCRBY 只出现在播放量写入脚本里（写当日播放量哈希），它停止增长说明 Redis 的播放量写入停止。
  压测机通知压测结束之后，两个值连续 100 次（10 秒）都没有变化，就判定写入停止；
  停止时刻取这 100 次中第一次查询的时间，也就是最后一次写入之后的第一次查询。

  为什么等 10 秒：V1 及以后的版本每 5 秒才把本地队列里攒的播放量发往 MQ，两批之间可能有将近 5 秒没有任何写入；
  等待窗口必须比 5 秒长，否则会在两批之间误判为"已停止"。

  V0 在请求线程里同步写库，最后一次写入发生在压测机收到最后一个响应之前，
  所以 V0 的停止时刻可能早于压测结束时刻，算出的延迟接近 0 甚至为负，这是符合实际的。
"""
# 标准库
import csv
import os
import time

# 本项目
from core.time_util import iso_time
from repositories.play_count_repository import PlayCountRepository
from schemas.write_stop_result import WriteStopResult
from services.signal_service import SignalService

POLL_INTERVAL = 0.1  # 每秒 10 次
STABLE_POLLS = 100  # 连续 100 次没有变化即判定停止
DRAIN_TIMEOUT = 30 * 60  # 压测结束后最多再等 30 分钟


class WriteStopService:
    """判定播放量写入何时停止"""

    def __init__(self, repo: PlayCountRepository, out_dir: str) -> None:
        self.repo = repo
        self.out_dir = out_dir  # 写入进度曲线 poll-series.csv 的输出目录

    def wait_until_stopped(self, signals: SignalService) -> WriteStopResult:
        """serve.py 的主线程在记好起点后调用：每 0.1 秒查询一次，直到判定写入停止。
        signals 用来得知压测机是否已通知压测结束"""
        prev: tuple[int, int] | None = None  # 上一次查询的 (MySQL 总和, HINCRBY 次数)
        unchanged = 0  # 连续多少次没有变化
        streak_start: float | None = None  # 最近一次看到新值的那次查询的时间，即这一串"没有变化"的起点
        # 监控本身的开销：查询次数、单次查询的总耗时和最长耗时
        polls = 0
        cost_sum = 0.0
        cost_max = 0.0
        # 每次看到新值就记一行，事后可以画出 MySQL、Redis 写入进度的曲线
        with open(os.path.join(self.out_dir, 'poll-series.csv'), 'w', newline='') as f:
            series = csv.writer(f)
            series.writerow(['time_iso', 'mysql_sum', 'hincrby_calls'])
            next_tick = time.time()  # 下一次查询的预定时间
            while True:
                t = time.time()
                cur = (self.repo.sum_play_count(), self.repo.count_hincrby_calls())
                cost = time.time() - t
                polls += 1
                cost_sum += cost
                cost_max = max(cost_max, cost)
                # 元组用 == 比较时逐个元素比较：两个值都没变才算没变
                if cur == prev:
                    unchanged += 1
                else:
                    unchanged = 0
                    streak_start = t
                    series.writerow([iso_time(t), cur[0], cur[1]])
                prev = cur
                # 压测机通知压测结束之后才开始判定，压测进行中偶尔停顿不算停止
                if signals.is_load_finished():
                    stopped = unchanged >= STABLE_POLLS
                    # 30 分钟还没停下来：按超时处理，结果里会标出
                    timed_out = not stopped and t - signals.load_report['received'] > DRAIN_TIMEOUT
                    if stopped or timed_out:
                        return WriteStopResult(
                            stop_time=streak_start,
                            mysql_sum=cur[0],
                            hincrby_calls=cur[1],
                            timed_out=timed_out,
                            polls=polls,
                            avg_poll_ms=round(cost_sum / polls * 1000, 2),
                            max_poll_ms=round(cost_max * 1000, 2),
                        )
                # 按固定节拍查询：下一次在上一次的预定时间上加 0.1 秒，而不是查完再睡 0.1 秒，
                # 这样查询本身的耗时不会让间隔越拉越长；某次查询超过 0.1 秒时，下一次立即开始
                next_tick += POLL_INTERVAL
                time.sleep(max(0.0, next_tick - time.time()))
