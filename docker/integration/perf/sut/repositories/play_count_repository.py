"""PlayCountRepository：MySQL、Redis 的播放量查询，只读不写。"""
# 标准库
import csv

# 第三方库
import redis
from sqlalchemy import func, select

# 本项目
from core.config import DAILY_PREFIX, REDIS
from core.database import engine
from models.video_info import VideoInfo


class PlayCountRepository:
    """MySQL、Redis 的播放量查询"""

    def __init__(self) -> None:
        # 整个测量期间复用同一个 MySQL 连接：每 0.1 秒查一次，省去每次从连接池借出、归还连接的开销
        self.conn = engine.connect()
        self.redis = redis.Redis(**REDIS)  # Redis 客户端，相当于 Jedis

    def sum_play_count(self) -> int:
        """MySQL 里所有视频的播放量总和"""
        # 相当于 SELECT COALESCE(SUM(play_count), 0) FROM video_info。表为空时 SUM 返回 NULL，COALESCE 把它换成 0
        stmt = select(func.coalesce(func.sum(VideoInfo.play_count), 0))
        # scalar() 取结果的第一行第一列；MySQL 的 SUM 返回的是小数类型，转成整数
        return int(self.conn.scalar(stmt))

    def count_hincrby_calls(self) -> int:
        """Redis 自启动以来执行 HINCRBY 的累计次数。HINCRBY 只出现在播放量写入脚本里，它停止增长说明 Redis 的播放量写入停止"""
        # INFO commandstats 返回每种命令自 Redis 启动以来的累计执行次数，解析后形如
        #   {'cmdstat_hincrby': {'calls': 12345, 'usec': ...}, 'cmdstat_get': {...}, ...}
        # 读这个统计不需要扫描任何 key，开销固定，不随数据量增长
        stats = self.redis.info('commandstats')
        # .get(键, 默认值)。还没执行过 HINCRBY 时没有这一项，按 0 计
        return int(stats.get('cmdstat_hincrby', {}).get('calls', 0))

    def dump_play_counts(self, path: str) -> int:
        """全量读取 MySQL 所有视频的播放量，写入 CSV（留档，便于逐个视频核对），返回总和"""
        # 相当于 SELECT video_id, play_count FROM video_info ORDER BY video_id
        stmt = select(VideoInfo.video_id, VideoInfo.play_count).order_by(VideoInfo.video_id)
        total = 0
        # newline='' 让 csv 模块自己处理换行，避免在 Windows 上多出空行
        with open(path, 'w', newline='') as f:
            w = csv.writer(f)
            w.writerow(['video_id', 'play_count'])
            # 每行是 (video_id, play_count)，for 里直接拆成两个变量
            for vid, pc in self.conn.execute(stmt):
                w.writerow([vid, pc])
                total += pc or 0  # play_count 为 NULL（None）时按 0 计
        return total

    def sum_daily_play_counts(self, days: set[str], path: str | None = None) -> int:
        """读取 Redis 当日播放量（HASH 结构），返回所有视频播放量的总和；给了 path 就同时把每个视频的数据写入 CSV。

        days：要读取的日期集合，如 {'2026-10-04'}；压测跨过 UTC 零点时会有两个日期。
        哈希的结构：key 是 nilo:{play-count}:daily:<日期>，字段是视频 ID，值是这个视频当天的播放量。
        """
        total = 0
        f = open(path, 'w', newline='') if path else None
        w = csv.writer(f) if f else None
        if w:
            w.writerow(['day', 'video_id', 'daily_play_count'])
        for day in sorted(days):
            # hscan_iter 在内部反复执行 HSCAN，每次取约 1000 个字段，边取边交给循环处理。
            # 不用 HGETALL 一次读出整个哈希，是因为哈希很大时 HGETALL 会长时间占住 Redis
            for vid, cnt in self.redis.hscan_iter(DAILY_PREFIX + day, count=1000):
                total += int(cnt)
                if w:
                    w.writerow([day, vid, cnt])
        if f:
            f.close()
        return total
