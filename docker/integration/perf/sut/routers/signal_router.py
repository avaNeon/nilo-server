"""信号服务的 HTTP 接口，压测机通过它和被测机配合完成一次测量，相当于 Spring 的 @RestController。

压测机按顺序调用 4 个接口：
  GET  /verify-ready   压测机询问：被测机准备好了吗？
  POST /start-monitor  压测机通知：要开始压测了，记好起点、唤醒主线程后返回起点
  POST /load-finished  压测机通知：压测结束了，附带结束时刻和放行请求数
  GET  /result         压测机索要结果：还没算完返回 202，算完返回 200 和结果

接口通过 SignalService 唤醒 serve.py 的主线程、取走结果；记起点调用 MeasurementService。
"""
# 标准库
from typing import Annotated

# 第三方库
from fastapi import APIRouter, Depends, HTTPException, Request, Response

# 本项目
from schemas.load_report import LoadReport
from services.measurement_service import MeasurementService
from services.signal_service import SignalService

router = APIRouter()


def get_signal_service(request: Request) -> SignalService:
    """取得 serve.py 创建并挂在 app.state 上的 SignalService，供下面的依赖注入使用"""
    return request.app.state.signal_service


def get_measurement_service(request: Request) -> MeasurementService:
    """取得 serve.py 创建并挂在 app.state 上的 MeasurementService，供下面的依赖注入使用"""
    return request.app.state.measurement_service


# 接口参数声明为 signals: SignalServiceDep，FastAPI 就会调用 get_signal_service 把 SignalService 注入进来，相当于 @Autowired
SignalServiceDep = Annotated[SignalService, Depends(get_signal_service)]
MeasurementServiceDep = Annotated[MeasurementService, Depends(get_measurement_service)]


@router.get('/verify-ready')
def verify_ready() -> dict:
    """压测机询问：被测机准备好了吗？
    压测机启动后反复调用，直到返回 200 才开始压测。信号服务是被测机工作流的最后一步，
    启动环境、灌数据、等 ES 同步都完成后才会运行，所以只要能响应就说明已就绪"""
    return {'ready': True}


@router.post('/start-monitor')
def start_monitor(signals: SignalServiceDep, measurement: MeasurementServiceDep) -> dict:
    """压测机通知：要开始压测了。
    在预热之后、正式压测之前调用：记好起点，唤醒主线程开始监控，再把起点返回给压测机。
    压测机收到返回才开始压测，这样 k6 的请求不会在记起点之前写进库，对账才对得上。
    压测机只调用一次；万一重复调用，直接返回第一次的起点"""
    if signals.baseline is None:
        signals.start(measurement.take_baseline())
    return signals.baseline


@router.post('/load-finished')
def load_finished(report: LoadReport, signals: SignalServiceDep) -> dict:
    """压测机通知：压测结束了。
    在 k6 结束后调用，请求体是 k6 的简要结果（见 LoadReport）。还没调用过 /start-monitor 时返回 409：没有起点就没法对账"""
    if not signals.finish_load(report):
        raise HTTPException(status_code=409, detail='monitor not started')
    return {'ok': True}


@router.get('/result')
def get_result(response: Response, signals: SignalServiceDep) -> dict:
    """压测机索要结果。
    发完 /load-finished 后反复调用：还没算完返回 202，算完返回 200 和结果"""
    result = signals.fetch_result()
    if result is None:
        response.status_code = 202
        return {'finished': False}
    return result
