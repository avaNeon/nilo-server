"""等待 ES 同步，两种用法：
  --count N      灌完种子数据后：ES 里的文档数达到 N，说明 MySQL → Canal → ES 这条链路是通的。工作流的 "Wait for ES sync" 步骤运行
  --play-count   两轮压测之间：ES 里的播放量总和等于 MySQL，说明上一轮的写入已经全部同步到 ES。run_rounds.sh 换版本前运行；
                 不等的话，Canal 同步上一轮写入的工作会延续到下一轮，占用下一轮的 CPU
用法：python wait_for_es_sync.py --count N
     python wait_for_es_sync.py --play-count
"""
# 标准库
import argparse
import json
import sys
import time
import urllib.request
from collections.abc import Callable

# 本项目
from core.config import ES_AUTH, ES_INDEX, ES_URL
from core.log import log
from repositories.play_count_repository import PlayCountRepository

TIMEOUT = 20 * 60  # --count 最多等 20 分钟
PLAY_COUNT_TIMEOUT = 10 * 60  # --play-count 最多等 10 分钟：等不到也只是让下一轮多一份 Canal 的开销，不值得白等太久
INTERVAL = 2  # 每 2 秒查一次


def main() -> None:
    """主流程：按用法选好要等的条件，每 2 秒检查一次，满足就正常退出；超时还不满足就以退出码 1 退出，工作流这一步会显示失败"""
    p = argparse.ArgumentParser()
    # 两种用法二选一，必须给一个
    usage = p.add_mutually_exclusive_group(required=True)
    usage.add_argument('--count', type=int)
    usage.add_argument('--play-count', action='store_true')
    a = p.parse_args()
    if a.play_count:
        check, timeout = play_count_synced(PlayCountRepository()), PLAY_COUNT_TIMEOUT
    else:
        check, timeout = documents_synced(a.count), TIMEOUT

    deadline = time.time() + timeout
    last = None
    while time.time() < deadline:
        done, progress = check()
        if progress != last:  # 进度有变化时才打印
            log(progress)
            last = progress
        if done:
            return
        time.sleep(INTERVAL)
    # sys.exit 传入字符串：把它打印到标准错误，并以退出码 1 结束进程
    sys.exit(f'ES 同步超时：{last}')


# ---------------- 两种用法要等的条件 ----------------
# 每个函数返回一个检查函数，main() 每 2 秒调用一次，得到 (是否满足, 进度文本)

def documents_synced(count: int) -> Callable[[], tuple[bool, str]]:
    """--count：ES 里的文档数达到 count"""
    def check() -> tuple[bool, str]:
        c = count_es_documents()
        return c >= count, f'ES documents: {c}/{count}'
    return check


def play_count_synced(repo: PlayCountRepository) -> Callable[[], tuple[bool, str]]:
    """--play-count：ES 里的播放量总和等于 MySQL 的播放量总和。种子视频的播放量从 0 开始，Canal 不过滤任何行，同步完两边一定相等"""
    def check() -> tuple[bool, str]:
        es, mysql = sum_es_play_count(), repo.sum_play_count()
        return es == mysql, f'play count sum: ES {es} / MySQL {mysql}'
    return check


# ---------------- 查询 ES ----------------
# ES 还没启动好、索引还不存在等任何异常都返回 -1，让 main() 继续等

def count_es_documents() -> int:
    """ES 索引里的文档数"""
    req = urllib.request.Request(f'{ES_URL}/{ES_INDEX}/_count', headers={'Authorization': ES_AUTH})
    try:
        with urllib.request.urlopen(req, timeout=5) as r:
            # 返回体形如 {"count": 100000, ...}
            return json.load(r)['count']
    except Exception:
        return -1


def sum_es_play_count() -> int:
    """ES 索引里所有文档的播放量（playCount 字段）总和"""
    # size=0：不要文档本身，只要聚合结果；sum 聚合把所有文档的 playCount 加起来
    body = json.dumps({'size': 0, 'aggs': {'total': {'sum': {'field': 'playCount'}}}}).encode()
    req = urllib.request.Request(f'{ES_URL}/{ES_INDEX}/_search', data=body,
                                 headers={'Authorization': ES_AUTH, 'Content-Type': 'application/json'})
    try:
        with urllib.request.urlopen(req, timeout=5) as r:
            # 返回体形如 {"aggregations": {"total": {"value": 123456.0}}, ...}；sum 聚合的结果是小数，转成整数
            return int(json.load(r)['aggregations']['total']['value'])
    except Exception:
        return -1


if __name__ == '__main__':
    main()
