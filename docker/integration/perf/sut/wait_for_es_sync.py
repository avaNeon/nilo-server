"""等待 ES 同步：种子视频插入 MySQL 后，Canal 会把它们同步到 ES。ES 里的文档数达到 N，说明 MySQL → Canal → ES 这条链路是通的。
工作流的 "Wait for ES sync" 步骤运行它。
用法：python wait_for_es_sync.py --count N
"""
# 标准库
import argparse
import json
import sys
import time
import urllib.request

# 本项目
from core.config import ES_AUTH, ES_INDEX, ES_URL
from core.log import log

TIMEOUT = 20 * 60  # 最多等 20 分钟
INTERVAL = 2  # 每 2 秒查一次


def main() -> None:
    """主流程：每 2 秒查一次 ES 文档数，达到 N 就正常退出；20 分钟还没达到就以退出码 1 退出，工作流这一步会显示失败"""
    p = argparse.ArgumentParser()
    p.add_argument('--count', type=int, required=True)
    count = p.parse_args().count

    deadline = time.time() + TIMEOUT
    last = None
    while time.time() < deadline:
        c = count_es_documents()
        if c != last:  # 文档数有变化时才打印进度
            log(f'ES documents: {c}/{count}')
            last = c
        if c >= count:
            return
        time.sleep(INTERVAL)
    # sys.exit 传入字符串：把它打印到标准错误，并以退出码 1 结束进程
    sys.exit(f'ES 同步超时：{last}/{count}')


def count_es_documents() -> int:
    """main() 每 2 秒调用一次：查询 ES 索引里的文档数。ES 还没启动好、索引还不存在等任何异常都返回 -1，让 main() 继续等"""
    req = urllib.request.Request(f'{ES_URL}/{ES_INDEX}/_count', headers={'Authorization': ES_AUTH})
    try:
        with urllib.request.urlopen(req, timeout=5) as r:
            # 返回体形如 {"count": 100000, ...}
            return json.load(r)['count']
    except Exception:
        return -1


if __name__ == '__main__':
    main()
