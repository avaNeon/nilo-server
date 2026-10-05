"""从压测机上的 Prometheus 汇总测试窗口内的关键指标，写成 JSON。工作流的 "Summarize metrics" 步骤运行它。
用法：python summarize_metrics.py --start <秒级时间戳> --end <秒级时间戳> --out prom-summary.json

对下面 QUERIES 里的每条 PromQL，向 Prometheus 的 HTTP 接口发一次查询，
时间点取测试结束时刻，每条查询都"往回看"整个测试窗口（从基线到写入停止），得到窗口内的峰值、平均值或增量。
生成的 prom-summary.json 由 generate_report.py 读取，写进报告。
"""
# 标准库
import argparse
import json
import urllib.parse
import urllib.request

# Prometheus 只监听压测机本机，见同目录的 docker-compose.yaml
PROM = 'http://127.0.0.1:9090'


def main() -> None:
    """主流程：逐条执行 QUERIES 里的 PromQL，汇总成一个 JSON 文件"""
    p = argparse.ArgumentParser()
    p.add_argument('--start', type=float, required=True)   # 测试窗口开始：基线时刻
    p.add_argument('--end', type=float, required=True)     # 测试窗口结束：被测机判定写入停止、压测机拿到结果的时刻
    p.add_argument('--out', required=True)
    a = p.parse_args()

    # 窗口长度（秒），至少 15 秒，保证 rate(...[15s]) 有足够的数据点
    window = max(15, int(a.end - a.start))
    summary = {'window_s': window}
    # 逐条执行 QUERIES 里的查询。某条失败只记录错误，不影响其他查询，报告里这一项显示为 "-"
    for name, expr in QUERIES.items():
        try:
            summary[name] = query(expr.format(w=window), a.end)
        except Exception as e:
            summary[name] = {'error': repr(e)}

    with open(a.out, 'w') as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)
    # 同时打印到日志，方便直接在 GitHub Actions 页面查看
    print(json.dumps(summary, ensure_ascii=False, indent=2))


# 指标名 -> PromQL。
# 每条 PromQL 里的 {w} 会被替换成窗口长度（秒）。因为用 Python 的 .format() 做替换，
# PromQL 自己的花括号（标签过滤）要写成两个 {{ }}，替换后会变回一个。
#
# PromQL 速查（Prometheus 的查询语言）：
#   - 很多指标是"累计值"（只增不减的计数器），例如"容器自启动以来用掉的 CPU 秒数"
#   - rate(x[15s])：计数器在最近 15 秒内平均每秒增长多少。对 CPU 秒数求 rate，得到"正在用几个核"
#   - increase(x[N秒])：计数器在最近 N 秒内一共增长了多少
#   - sum by (name) (...)：按 name 标签分组求和，每个容器一个结果
#   - max_over_time(x[N秒]) / min_over_time / avg_over_time：最近 N 秒内的最大值 / 最小值 / 平均值
#   - x[N秒:5s]：子查询，在最近 N 秒内每隔 5 秒算一次 x，再交给外层的 max_over_time 等函数
#   - {name!=""}：标签过滤，只要 name 标签不为空的数据
QUERIES = {
    # 各容器 CPU 峰值、平均值（单位：核，1.0 表示占满一个核）。
    # cAdvisor 也会报告宿主机自身和系统进程，它们没有 name 标签，用 name!="" 排除
    'container_cpu_cores_max': 'max_over_time(sum by (name) (rate(container_cpu_usage_seconds_total{{name!=""}}[15s]))[{w}s:5s])',
    'container_cpu_cores_avg': 'avg_over_time(sum by (name) (rate(container_cpu_usage_seconds_total{{name!=""}}[15s]))[{w}s:5s])',
    # 各容器内存峰值（MiB）。working set 是容器实际在用、无法回收的内存，与 docker stats 显示的口径基本一致
    'container_memory_max_mib': 'max_over_time(container_memory_working_set_bytes{{name!=""}}[{w}s]) / 1048576',
    # 各微服务 JVM 堆内存峰值（MiB）：把 Eden、Survivor、老年代等各个堆区加起来
    'jvm_heap_used_max_mib': 'max_over_time(sum by (application) (jvm_memory_used_bytes{{area="heap"}})[{w}s:5s]) / 1048576',
    # 各微服务在窗口内 GC 暂停的总时长（秒）
    'jvm_gc_pause_seconds_total': 'sum by (application) (increase(jvm_gc_pause_seconds_sum[{w}s]))',
    # 播放量队列的积压峰值：ready 是还没投递给消费者的消息，unacked 是已投递、消费者还没确认的消息
    'play_count_queue_ready_max': 'max_over_time(rabbitmq_queue_messages_ready{{queue="video.play-count.queue"}}[{w}s])',
    'play_count_queue_unacked_max': 'max_over_time(rabbitmq_queue_messages_unacked{{queue="video.play-count.queue"}}[{w}s])',
    # MySQL 每秒更新行数的峰值（InnoDB 累计更新行数求 rate）
    'mysql_rows_updated_per_s_max': 'max_over_time(rate(mysql_global_status_innodb_row_ops_total{{operation="updated"}}[15s])[{w}s:5s])',
    # MySQL 每秒执行语句数的峰值。包含被测机监控脚本自己的查询（每秒 10 次）
    'mysql_queries_per_s_max': 'max_over_time(rate(mysql_global_status_queries[15s])[{w}s:5s])',
    # Redis 每秒处理命令数的峰值。包含被测机监控脚本自己的 INFO 查询（每秒 10 次）
    'redis_commands_per_s_max': 'max_over_time(rate(redis_commands_processed_total[15s])[{w}s:5s])',
    # 每个抓取目标的 up 指标：抓取成功为 1，失败为 0。
    # 窗口内最小值为 0，说明这期间有抓取失败，对应组件的指标有缺口
    'scrape_up_min': 'min_over_time(up[{w}s])',
}


def query(expr: str, at: float) -> dict[str, float]:
    """main() 对每条 PromQL 调用一次：执行查询，返回 {分组名: 数值}，例如 {'nilo-web': 1.234, 'mysql': 0.5}。

    expr：PromQL；at：查询的时间点（秒级时间戳）。
    Prometheus 返回的 JSON 形如：
      {"status": "success", "data": {"result": [
          {"metric": {"name": "nilo-web", ...其他标签}, "value": [时间戳, "1.234"]},
          ...
      ]}}
    """
    url = f'{PROM}/api/v1/query?' + urllib.parse.urlencode({'query': expr, 'time': at})
    with urllib.request.urlopen(url, timeout=30) as r:
        data = json.load(r)
    out = {}
    for item in data['data']['result']:
        # 去掉 __name__（指标名）这一项，剩下的都是标签
        labels = {k: v for k, v in item['metric'].items() if k != '__name__'}
        # 选一个标签作为分组名：容器名 > 应用名 > 抓取任务名 > 队列名，都没有就叫 value。
        # a or b or c 返回第一个不为空的值
        key = labels.get('name') or labels.get('application') or labels.get('job') or labels.get('queue') or 'value'
        # value 是 [时间戳, "数值字符串"]，[1] 取数值，转成小数并保留 3 位
        out[key] = round(float(item['value'][1]), 3)
    return out


if __name__ == '__main__':
    main()
