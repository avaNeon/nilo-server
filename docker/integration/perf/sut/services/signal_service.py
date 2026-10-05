"""SignalService：在接口（uvicorn 的线程）和 serve.py 的主线程之间传递起点、压测机的结束信息和测量结果。

主线程一条线往下走，每一步等压测机的一个指令。按时间顺序，两边的配合是：
  ① 开始      /start-monitor 记好起点后调用 start()  →  主线程的 wait_for_start() 返回起点
  ② 压测结束  /load-finished 调用 finish_load()      →  主线程判定写入停止时，通过 is_load_finished() 得知压测已结束
  ③ 交出结果  主线程 publish_result_and_wait()       →  /result 调用 fetch_result() 取走，主线程的等待随之结束

两个线程之间用 threading.Event 通知，相当于 Java 的 CountDownLatch(1)：set() 点亮，wait() 等它亮，is_set() 查看是否已亮。
"""
# 标准库
import threading
import time

# 本项目
from core.log import log
from schemas.load_report import LoadReport


class SignalService:
    """起点、压测机的结束信息、测量结果"""

    def __init__(self) -> None:
        self.baseline: dict | None = None  # 起点，/start-monitor 记好后填入
        self.load_report: dict | None = None  # 压测机发来的结束信息，收到 /load-finished 时填入
        self.result: dict | None = None  # 测量结果，主线程对账后填入
        self._started = threading.Event()  # 起点已记好
        self._result_fetched = threading.Event()  # 压测机已取走结果

    # ---------------- ① 开始 ----------------

    def start(self, baseline: dict) -> None:
        """/start-monitor 记好起点后调用：保存起点，唤醒主线程"""
        self.baseline = baseline
        self._started.set()

    def wait_for_start(self) -> dict:
        """主线程调用：等压测机通知"要开始压测了"，返回起点"""
        self._started.wait()
        return self.baseline

    # ---------------- ② 压测结束 ----------------

    def finish_load(self, report: LoadReport) -> bool:
        """/load-finished 调用：保存压测机的结束信息（结束时刻、放行请求数）。还没记好起点时返回 False"""
        if self.baseline is None:
            return False
        # model_dump() 把 LoadReport 转成字典（含未声明的字段），再加上收到通知的时间（被测机的时间），用于排空超时的判断
        self.load_report = report.model_dump() | {'received': time.time()}
        log(f'load finished: {self.load_report}')
        return True

    def is_load_finished(self) -> bool:
        """WriteStopService 每次查询后调用：压测机是否已通知压测结束"""
        return self.load_report is not None

    # ---------------- ③ 交出结果 ----------------

    def publish_result_and_wait(self, result: dict, timeout: float) -> None:
        """主线程对账后调用：交出结果（之后 /result 就能取到），然后等压测机取走，最多等 timeout 秒"""
        self.result = result
        self._result_fetched.wait(timeout)

    def fetch_result(self) -> dict | None:
        """/result 调用：结果还没交出返回 None；交出了就返回结果，并标记为已取走"""
        if self.result is None:
            return None
        self._result_fetched.set()
        return self.result
