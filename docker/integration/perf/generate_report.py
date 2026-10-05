"""合并被测机与压测机的结果，生成 Markdown 报告，显示在 GitHub Actions 这次运行的 Summary 页面。工作流的 report 任务运行它。
用法：python generate_report.py --sut <被测机结果目录> --load <压测机结果目录> --title <标题> >> $GITHUB_STEP_SUMMARY

读取的文件：
  被测机目录  sut-result.json      写入完成延迟、对账结果、监控开销（sut/services/measurement_service.py 生成）
  压测机目录  k6-main-brief.json   正式压测的请求数、响应时间（load/load.js 生成）
             prom-summary.json    被测机各组件的资源与链路指标（load/summarize_metrics.py 生成）
             load-cpu.csv         压测机 CPU 采样（load/sample_cpu.py 生成）
             tailscale-ping.txt   两台机器之间的网络情况
任何一个文件缺失（比如某个任务中途失败）都不会报错，对应的项显示为 "-"。
"""
# 标准库
import argparse
import csv
import json
import math
import os

# 压测机 CPU 的 P95 达到这个值，判定压测机可能成为瓶颈
LOAD_CPU_LIMIT = 80.0


def main() -> None:
    """主流程：读取两台机器的结果文件 → 依次生成报告的各个部分 → 打印"""
    p = argparse.ArgumentParser()
    p.add_argument('--sut', required=True)
    p.add_argument('--load', required=True)
    p.add_argument('--title', required=True)
    a = p.parse_args()

    # 1. 读取两台机器的结果文件。"x or {}"：文件缺失时用空字典代替，后面取值时就不用到处判空
    sut = read_json(os.path.join(a.sut, 'sut-result.json')) or {}
    k6 = read_json(os.path.join(a.load, 'k6-main-brief.json')) or {}
    prom = read_json(os.path.join(a.load, 'prom-summary.json')) or {}
    load_cpu = read_load_cpu(os.path.join(a.load, 'load-cpu.csv'))
    net = read_text(os.path.join(a.load, 'tailscale-ping.txt'))

    # 2. 依次生成报告的各个部分，每个部分是若干行文本
    lines = [f'## {a.title}', '']
    lines += conclusion_section(sut, load_cpu)          # 结论：写入完成延迟、对账、本次结果是否可信
    lines += k6_section(k6)                             # k6 的请求数和响应时间
    lines += resource_section(prom)                     # 被测机每个容器的 CPU 和内存
    lines += chain_section(prom)                        # 链路各环节的指标，用来定位瓶颈
    lines += measurement_section(sut, load_cpu, net)    # 测量本身的可信度

    # 3. 打印到标准输出，工作流用 >> 追加到 $GITHUB_STEP_SUMMARY
    print('\n'.join(lines))


# ---------------- 报告的各个部分 ----------------

def conclusion_section(sut: dict, load_cpu: dict | None) -> list[str]:
    """结论：写入完成延迟、对账结果、压测机 CPU；被测机监控出错或压测机 CPU 接近跑满时加醒目提示"""
    rec = sut.get('reconcile', {})
    load_cpu_text = f'{load_cpu["avg"]:.1f}% / {load_cpu["p95"]:.1f}% / {load_cpu["max"]:.1f}%' if load_cpu else '-'
    lines = ['### 结论', '', table([
        ['写入完成延迟（秒）', fmt(sut.get('write_complete_latency_s'), 3)],
        ['压测结束时刻（压测机）', sut.get('load_end_iso')],
        ['写入停止时刻（被测机）', sut.get('write_stop_iso')],
        ['对账', '通过' if rec.get('ok') else '不通过'],
        ['放行请求数', rec.get('accepted_requests')],
        ['MySQL 播放量增量', rec.get('mysql_play_count_delta')],
        ['Redis 当日播放量增量', rec.get('redis_daily_delta')],
        ['排空是否超时', '是' if sut.get('drain_timed_out') else '否'],
        ['压测机 CPU 平均 / P95 / 峰值', load_cpu_text],
    ], ['项目', '值']), '']
    # 以 > 开头的行在 Markdown 里显示为醒目的引用块
    if sut.get('error'):
        lines += [f'> 被测机监控出错：`{sut["error"]}`', '']
    if load_cpu and load_cpu['p95'] >= LOAD_CPU_LIMIT:
        lines += [f'> 压测机 CPU 的 P95 达到 {load_cpu["p95"]:.1f}%，接近跑满：k6 自身可能成为瓶颈，本次的响应时间和吞吐不可信。', '']
    return lines


def k6_section(k6: dict) -> list[str]:
    """k6 的统计：请求数、放行 / 被限流 / 失败、响应时间分位。
    dropped 是 k6 没能按时发出的请求数，不为 0 说明发压没有达到目标速率"""
    lat = k6.get('latency_ms', {})
    return ['### 压测（k6）', '', table([
        ['每秒请求数（目标）', k6.get('rate')],
        ['持续时间', k6.get('duration')],
        ['视频分布', k6.get('distribution')],
        ['总请求数', k6.get('requests')],
        ['放行 / 被限流 / 失败', f'{k6.get("accepted")} / {k6.get("rejected")} / {k6.get("failed")}'],
        ['未能按时发出的请求（dropped）', k6.get('dropped_iterations')],
        ['响应时间 平均 / 中位数（ms）', f'{fmt(lat.get("avg"), 2)} / {fmt(lat.get("med"), 2)}'],
        ['响应时间 P90 / P95 / P99（ms）', f'{fmt(lat.get("p90"), 2)} / {fmt(lat.get("p95"), 2)} / {fmt(lat.get("p99"), 2)}'],
        ['响应时间 最大（ms）', fmt(lat.get('max'), 2)],
    ], ['项目', '值']), '']


def resource_section(prom: dict) -> list[str]:
    """被测机每个容器的 CPU 和内存，按 CPU 峰值从高到低排列。没有数据或查询失败时不输出这一部分"""
    cpu_max = prom_values(prom, 'container_cpu_cores_max')
    if not cpu_max:
        return []
    cpu_avg = prom_values(prom, 'container_cpu_cores_avg') or {}
    mem_max = prom_values(prom, 'container_memory_max_mib') or {}
    # set(cpu_max) | set(mem_max)：两边容器名的并集；key=lambda ...：按 CPU 峰值的相反数排序，即从高到低
    names = sorted(set(cpu_max) | set(mem_max), key=lambda n: -cpu_max.get(n, 0))
    return ['### 被测机资源（测试窗口内）', '',
            table([[n, cpu_avg.get(n), cpu_max.get(n), mem_max.get(n)] for n in names],
                  ['容器', 'CPU 平均（核）', 'CPU 峰值（核）', '内存峰值（MiB）']), '']


def chain_section(prom: dict) -> list[str]:
    """链路各环节的指标：JVM、MQ 积压、MySQL、Redis，用来定位瓶颈"""
    return ['### 链路指标（测试窗口内）', '', table([
        ['JVM 堆峰值（MiB）', prom_line(prom, 'jvm_heap_used_max_mib')],
        ['GC 暂停累计（秒）', prom_line(prom, 'jvm_gc_pause_seconds_total')],
        ['播放量队列积压峰值（待消费 / 处理中）',
         f'{prom_line(prom, "play_count_queue_ready_max")} / {prom_line(prom, "play_count_queue_unacked_max")}'],
        ['MySQL 每秒更新行数峰值', prom_line(prom, 'mysql_rows_updated_per_s_max')],
        ['MySQL 每秒查询数峰值', prom_line(prom, 'mysql_queries_per_s_max')],
        ['Redis 每秒命令数峰值', prom_line(prom, 'redis_commands_per_s_max')],
    ], ['指标', '值']), '']


def measurement_section(sut: dict, load_cpu: dict | None, net: str) -> list[str]:
    """测量本身的可信度：监控查询有没有跟上 0.1 秒的节拍、压测机 CPU、两台机器之间的网络"""
    mon = sut.get('monitor', {})
    return ['### 测量本身', '',
            f'- 写入监控共查询 {mon.get("polls", "-")} 次，单次平均 {mon.get("avg_poll_ms", "-")} ms，最长 {mon.get("max_poll_ms", "-")} ms',
            '- 写入完成延迟 = 写入停止时刻（被测机）− 压测结束时刻（压测机），两台机器均通过 NTP 对时',
            # 相邻的几个字符串会自动拼成一个，用来把长句拆成多行书写
            f'- 压测机 CPU 在压测期间每秒采样一次，共 {load_cpu["seconds"] if load_cpu else "-"} 秒，'
            f'被云主机宿主占用（steal）平均 {fmt(load_cpu["steal_avg"]) if load_cpu else "-"}%；'
            f'P95 达到 {LOAD_CPU_LIMIT:.0f}% 即判定压测机可能成为瓶颈',
            # <details> 是可折叠的区块，默认收起，点开才显示 tailscale ping 的原始输出
            '', '<details><summary>两台机器之间的网络（tailscale ping）</summary>', '', '```', net or '-', '```', '</details>', '']


# ---------------- 读取 Prometheus 汇总中的一项 ----------------

def prom_values(prom: dict, name: str) -> dict[str, float] | None:
    """取一项指标的 {分组名: 数值}；没有数据或查询失败（summarize_metrics.py 写入了 {'error': ...}）时返回 None"""
    v = prom.get(name)
    if isinstance(v, dict) and v and 'error' not in v:
        return v
    return None


def prom_line(prom: dict, name: str) -> str:
    """把一项指标的所有分组格式化成一行，如 "nilo-web: 312.5, nilo-mq-consumer: 280.1"；没有数据时显示 -"""
    v = prom_values(prom, name)
    if not v:
        return '-'
    return ', '.join(f'{k}: {fmt(x)}' for k, x in v.items())


# ---------------- 读取文件 ----------------

def read_json(path: str) -> dict | None:
    """读取 JSON 文件，返回字典；文件不存在或内容不是合法 JSON 时返回 None"""
    try:
        with open(path, encoding='utf-8') as f:
            return json.load(f)
    except (OSError, ValueError):
        return None


def read_text(path: str) -> str:
    """读取文本文件并去掉首尾空白；文件不存在时返回空字符串"""
    try:
        with open(path, encoding='utf-8') as f:
            return f.read().strip()
    except OSError:
        return ''


def read_load_cpu(path: str) -> dict | None:
    """汇总压测机 CPU 采样：平均、P95（95% 的秒数不超过它，排除偶发尖峰）、峰值。文件不存在或为空时返回 None"""
    try:
        with open(path, encoding='utf-8') as f:
            # DictReader 把 CSV 的每一行读成字典，键是表头，如 {'time': '...', 'busy_pct': '35.2', ...}
            rows = list(csv.DictReader(f))
    except OSError:
        return None
    busy = sorted(float(r['busy_pct']) for r in rows)
    if not busy:
        return None
    return {
        'seconds': len(busy),             # 采样了多少秒
        'avg': sum(busy) / len(busy),
        # P95：排好序后第 ceil(0.95 × 个数) 个值（列表下标从 0 开始，所以减 1）
        'p95': busy[math.ceil(len(busy) * 0.95) - 1],
        'max': busy[-1],                  # 下标 -1 表示最后一个元素，排好序后就是最大值
        'steal_avg': sum(float(r['steal_pct']) for r in rows) / len(rows),
    }


# ---------------- 格式化 ----------------

def fmt(v: object, digits: int = 1) -> str:
    """把值格式化成表格里显示的文本：None 显示为 -，小数保留 digits 位，其他原样转成字符串"""
    if v is None:
        return '-'
    if isinstance(v, float):
        return f'{v:.{digits}f}'
    return str(v)


def table(rows: list[list], header: list[str]) -> str:
    """生成 Markdown 表格。rows 是二维列表（每个元素是一行），header 是表头"""
    lines = ['| ' + ' | '.join(header) + ' |', '|' + '---|' * len(header)]
    lines += ['| ' + ' | '.join(fmt(c) for c in r) + ' |' for r in rows]
    return '\n'.join(lines)


if __name__ == '__main__':
    main()
