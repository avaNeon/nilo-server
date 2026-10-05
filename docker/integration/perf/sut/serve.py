"""被测机信号服务的入口：配合压测机完成一次测量。工作流的 "Serve and monitor" 步骤在本目录运行它。
用法：python serve.py --bind <被测机隧道地址> --port 8099 --out <结果目录>

一次测量按顺序走完 main() 里的 ①～④，每一步等压测机的一个指令：
  ① 压测机通知"要开始压测了"（POST /start-monitor）：接口记下起点，唤醒主线程，再把起点返回给压测机
  ② 每 0.1 秒查一次数据库；压测机通知"压测结束"（POST /load-finished）后，连续 100 次不变就判定写入停止
  ③ 全量读取最终数据，与放行请求数对账
  ④ 交出结果，压测机取走（GET /result）后退出，退出码表示对账是否通过
压测机还会在开始前反复调用 GET /verify-ready，确认被测机已就绪。

主线程从上到下执行 ②～④；uvicorn 在后台线程接收压测机的请求，接口通过 SignalService 唤醒主线程、取走结果。

目录结构（分层与 FastAPI 项目一致，依赖见 requirements.txt）：
  seed_videos.py                          入口：灌种子数据（工作流第一个运行）
  wait_for_es_sync.py                     入口：等 ES 同步（工作流第二个运行）
  serve.py                                入口：信号服务（工作流最后运行，就是本文件）
  routers/signal_router.py                HTTP 接口，相当于 @RestController
  services/signal_service.py              SignalService：在接口和主线程之间传递起点、结束信息和结果
  services/measurement_service.py         MeasurementService：记录起点、对账
  services/write_stop_service.py          WriteStopService：判定写入何时停止
  repositories/play_count_repository.py   PlayCountRepository：MySQL、Redis 的播放量查询
  schemas/load_report.py                  LoadReport：/load-finished 的请求体，相当于 DTO
  schemas/write_stop_result.py            WriteStopResult：判定写入停止的结果，交给对账使用
  models/video_info.py                    VideoInfo：video_info 表的映射，相当于 @Entity
  core/                                   配置、数据库连接、日志、时间格式化
"""
# 标准库
import argparse
import os
import sys
import threading
import time

# 第三方库
import uvicorn
from fastapi import FastAPI

# 本项目
from core.log import log
from repositories.play_count_repository import PlayCountRepository
from routers import signal_router
from services.measurement_service import MeasurementService
from services.signal_service import SignalService
from services.write_stop_service import WriteStopService

RESULT_KEEP = 300  # 交出结果后，最多再等 5 分钟让压测机取走


def main() -> None:
    """主流程：一条线往下走，每一步等压测机的一个指令"""
    p = argparse.ArgumentParser()
    p.add_argument('--bind', required=True)
    p.add_argument('--port', type=int, default=8099)
    p.add_argument('--out', default='perf-results')
    a = p.parse_args()
    os.makedirs(a.out, exist_ok=True)

    repo = PlayCountRepository()
    measurement = MeasurementService(repo, a.out)
    write_stop = WriteStopService(repo, a.out)
    signals = SignalService()
    server, server_thread = start_server(signals, measurement, a.bind, a.port)

    # ① 等压测机通知"要开始压测了"。起点由 /start-monitor 接口记好后才唤醒这里，这里直接拿到起点
    baseline = signals.wait_for_start()

    try:
        # ② 每 0.1 秒查一次；压测机通知压测结束后，连续 100 次不变就判定写入停止
        stop = write_stop.wait_until_stopped(signals)
        # ③ 全量读取最终数据，与放行请求数对账
        result = measurement.reconcile(baseline, signals.load_report, stop)
    except Exception as e:
        # 出错也要交出一份结果，否则压测机会一直等下去
        log(f'measurement failed: {e!r}')
        result = {'error': repr(e), 'reconcile': {'ok': False}, 'drain_timed_out': False}

    # ④ 交出结果，等压测机取走，最多等 5 分钟；然后停止 HTTP 服务
    signals.publish_result_and_wait(result, RESULT_KEEP)
    time.sleep(1)  # 给最后一个响应留出发送完的时间
    server.should_exit = True
    server_thread.join()

    # 退出码：对账通过且没有超时为 0，否则为 1，工作流里这一步会显示失败
    sys.exit(0 if result['reconcile']['ok'] and not result['drain_timed_out'] else 1)


def start_server(signals: SignalService, measurement: MeasurementService,
                 host: str, port: int) -> tuple[uvicorn.Server, threading.Thread]:
    """main() 开头调用：创建 FastAPI 应用并挂上接口，在后台线程启动 uvicorn（HTTP 服务器），返回服务器和它所在的线程"""
    app = FastAPI()
    app.include_router(signal_router.router)
    # 接口通过依赖注入拿到这两个对象，见 routers/signal_router.py
    app.state.signal_service = signals
    app.state.measurement_service = measurement
    server = uvicorn.Server(uvicorn.Config(app, host=host, port=port))
    server_thread = threading.Thread(target=server.run, daemon=True)
    server_thread.start()
    return server, server_thread


if __name__ == '__main__':
    main()
