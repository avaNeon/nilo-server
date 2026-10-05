"""MeasurementService：记录压测前的起点（/start-monitor 接口调用），以及写入停止后的对账（serve.py 的主线程调用）。

对账：MySQL 播放量的增量、Redis 当日播放量的增量，都必须恰好等于放行请求数，一条不多、一条不少。
"""
# 标准库
import json
import os
import time

# 本项目
from core.log import log
from core.time_util import iso_time, utc_date
from repositories.play_count_repository import PlayCountRepository
from schemas.write_stop_result import WriteStopResult


class MeasurementService:
    """记录起点、对账"""

    def __init__(self, repo: PlayCountRepository, out_dir: str) -> None:
        self.repo = repo
        self.out_dir = out_dir  # 结果文件的输出目录

    def take_baseline(self) -> dict:
        """/start-monitor 接口调用：记录压测前的起点，包括 MySQL 播放量总和、Redis HINCRBY 累计次数、当日播放量哈希总和"""
        t = time.time()
        day = utc_date(t)
        baseline = {
            'time': t,
            'time_iso': iso_time(t),
            'mysql_sum': self.repo.sum_play_count(),  # 起点时的 MySQL 播放量总和（已包含预热产生的写入）
            'hincrby_calls': self.repo.count_hincrby_calls(),
            'day': day,
            'daily_sum': self.repo.sum_daily_play_counts({day}),  # 起点当天的播放量哈希总和
        }
        log(f'baseline: {baseline}')
        return baseline

    def reconcile(self, baseline: dict, load_report: dict, stop: WriteStopResult) -> dict:
        """主线程判定写入停止后调用：全量读取 MySQL 和 Redis 的最终数据，与放行请求数对账，写入 sut-result.json，返回结果。
        baseline：take_baseline() 的返回值；load_report：压测机发来的结束信息；stop：判定写入停止的结果"""
        # 要读取的日期：起点当天和停止当天。同一天时集合会自动去重，只剩一个
        days = {baseline['day'], utc_date(stop.stop_time)}
        mysql_total = self.repo.dump_play_counts(os.path.join(self.out_dir, 'final-mysql.csv'))
        daily_total = self.repo.sum_daily_play_counts(days, os.path.join(self.out_dir, 'final-redis-daily.csv'))
        accepted = load_report['accepted']  # 放行请求数：k6 统计的、接口返回成功的请求数
        end_ms = load_report['end_ms']  # 压测结束时刻（压测机的时间，毫秒）
        mysql_delta = mysql_total - baseline['mysql_sum']
        # 起点只读了起点当天的哈希；若跨 UTC 零点，新的一天从 0 开始，增量直接相加
        redis_delta = daily_total - baseline['daily_sum']
        result = {
            'baseline': baseline,
            'load_end_ms': end_ms,
            'load_end_iso': iso_time(end_ms / 1000),
            'write_stop_iso': iso_time(stop.stop_time),
            # 写入完成延迟 = 写入停止时刻（被测机的时间）- 压测结束时刻（压测机的时间），单位秒，保留 3 位小数
            'write_complete_latency_s': round(stop.stop_time - end_ms / 1000, 3),
            'drain_timed_out': stop.timed_out,
            'final_poll': {'mysql_sum': stop.mysql_sum, 'hincrby_calls': stop.hincrby_calls},
            'reconcile': {
                'accepted_requests': accepted,
                'mysql_play_count_delta': mysql_delta,
                'redis_daily_delta': redis_delta,
                'ok': mysql_delta == accepted and redis_delta == accepted,
            },
            # 监控本身的开销
            'monitor': {'polls': stop.polls, 'avg_poll_ms': stop.avg_poll_ms, 'max_poll_ms': stop.max_poll_ms},
            'load_report': load_report,  # 压测机发来的原始信息，原样留档
        }
        # ensure_ascii=False 让中文原样写出，而不是 \uXXXX
        with open(os.path.join(self.out_dir, 'sut-result.json'), 'w') as f:
            json.dump(result, f, ensure_ascii=False, indent=2)
        log('写入停止时刻：' + result['write_stop_iso'])
        log('最终数据：MySQL 播放量总和 %d（增量 %d），Redis 当日播放量增量 %d，放行请求 %s'
            % (mysql_total, mysql_delta, redis_delta, accepted))
        log('result: ' + json.dumps(result['reconcile'], ensure_ascii=False))
        return result
