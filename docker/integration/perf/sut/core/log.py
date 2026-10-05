"""日志"""
# 标准库
import time

# 本项目
from core.time_util import iso_time


def log(msg: str) -> None:
    """打印带时间的日志。flush=True：立即输出，GitHub Actions 的日志能实时看到"""
    print(f'[{iso_time(time.time())}] {msg}', flush=True)
