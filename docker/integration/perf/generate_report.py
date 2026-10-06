"""合并被测机与压测机的结果，生成 Markdown 报告，显示在 GitHub Actions 这次运行的 Summary 页面。工作流的 report 任务运行它。
用法：python generate_report.py --sut <被测机结果目录> --load <压测机结果目录> --title <标题> >> $GITHUB_STEP_SUMMARY

读取的文件：
  被测机目录  sut-result.json      写入完成延迟、对账结果、监控开销（sut/services/measurement_service.py 生成）
             sut-host.txt         被测机的 CPU 型号、核数（工作流的 Prepare host 步骤生成）
  压测机目录  k6-main-brief.json   正式压测的请求数、响应时间（load/load.js 生成）
             prom-summary.json    被测机各组件的资源与链路指标（load/summarize_metrics.py 生成）
             load-cpu.csv         压测机 CPU 采样（load/sample_cpu.py 生成）
             load-host.txt        压测机的 CPU 型号、核数（工作流的 Network check 步骤生成）
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
    hosts = {'被测机': read_host(os.path.join(a.sut, 'sut-host.txt')),
             '压测机': read_host(os.path.join(a.load, 'load-host.txt'))}

    # 2. 依次生成报告的各个部分，每个部分是若干行文本
    lines = [f'## {a.title}', '']
    lines += conclusion_section(sut, load_cpu)          # 结论：写入完成延迟、对账、本次结果是否可信
    lines += k6_section(k6, prom)                       # k6 的请求数和响应时间，以及 nilo-web 自己统计的响应时间
    lines += resource_section(prom)                     # 被测机每个容器的 CPU 和内存
    lines += chain_section(prom)                        # 链路各环节的指标，用来定位瓶颈
    lines += pools_section(prom)                        # Tomcat 线程、数据库连接池、GC 分类
    lines += redis_commands_section(prom, k6.get('accepted'))  # Redis 按命令类型的执行次数
    lines += measurement_section(sut, load_cpu, net, hosts)  # 测量本身的可信度、两台机器的配置

    # 3. 打印到标准输出，工作流用 >> 追加到 $GITHUB_STEP_SUMMARY
    print('\n'.join(lines))


# ---------------- 报告的各个部分 ----------------

def conclusion_section(sut: dict, load_cpu: dict | None) -> list[str]:
    """结论：写入完成延迟、对账结果、压测机 CPU；被测机监控出错或压测机 CPU 接近跑满时加醒目提示"""
    rec = sut.get('reconcile', {})
    load_cpu_text = f'{load_cpu["avg"]:.1f}% / {load_cpu["p95"]:.1f}% / {load_cpu["max"]:.1f}%' if load_cpu else '-'
    lines = ['### 结论', '', table([
        ['写入完成延迟（秒）', fmt(sut.get('write_complete_latency_s'), 3)],
        ['压测结束时刻（压测机收到最后一个响应）', sut.get('load_end_iso')],
        ['写入停止时刻（被测机）', sut.get('write_stop_iso')],
        ['对账', '通过' if rec.get('ok') else '不通过'],
        ['放行请求数', rec.get('accepted_requests')],
        ['MySQL 播放量增量', rec.get('mysql_play_count_delta')],
        ['Redis 当日播放量增量', rec.get('redis_daily_delta')],
        ['排空是否超时', '是' if sut.get('drain_timed_out') else '否'],
        ['压测机 CPU 使用率 平均 / P95 / 峰值', load_cpu_text],
    ], ['项目', '值']), '']
    # 以 > 开头的行在 Markdown 里显示为醒目的引用块
    if sut.get('error'):
        lines += [f'> 被测机监控出错：`{sut["error"]}`', '']
    if load_cpu and load_cpu['p95'] >= LOAD_CPU_LIMIT:
        lines += [f'> 压测机 CPU 使用率的 P95 达到 {load_cpu["p95"]:.1f}%，接近跑满：k6 自身可能成为瓶颈，本次的响应时间和吞吐不可信。', '']
    return lines


def k6_section(k6: dict, prom: dict) -> list[str]:
    """k6 的统计：请求数、放行 / 被限流 / 失败、响应时间分位；最后一行是 nilo-web 自己统计的响应时间，方便对照。
    dropped 是 k6 没能按时发出的请求数，不为 0 说明发压没有达到目标速率。
    k6 的响应时间 − 服务端响应时间 ≈ 网络往返 + 在 Tomcat 里排队等线程的时间"""
    lat = k6.get('latency_ms', {})
    server_avg = prom_value(prom, 'server_latency_avg_ms')
    server_p95 = prom_value(prom, 'server_latency_p95_ms')
    server_p99 = prom_value(prom, 'server_latency_p99_ms')
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
        ['服务端响应时间 平均 / P95 / P99（ms，nilo-web 统计，不含网络）',
         f'{fmt(server_avg, 2)} / {fmt(server_p95, 2)} / {fmt(server_p99, 2)}'],
    ], ['项目', '值']), '']


def resource_section(prom: dict) -> list[str]:
    """被测机每个容器的 CPU 和内存，按 CPU 峰值从高到低排列；宿主机上不在容器里的 nginx、dockerd 也列在一起。
    没有数据或查询失败时不输出这一部分"""
    cpu_max = prom_values(prom, 'container_cpu_cores_max')
    if not cpu_max:
        return []
    cpu_avg = prom_values(prom, 'container_cpu_cores_avg') or {}
    mem_max = prom_values(prom, 'container_memory_max_mib') or {}
    # 宿主机服务按 cgroup 路径查出来，换成可读的名字后并入容器的统计；| 合并两个字典
    cpu_max = cpu_max | host_values(prom, 'host_cpu_cores_max')
    cpu_avg = cpu_avg | host_values(prom, 'host_cpu_cores_avg')
    mem_max = mem_max | host_values(prom, 'host_memory_max_mib')
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


def pools_section(prom: dict) -> list[str]:
    """Tomcat 线程、数据库连接池、GC 分类，用来判断请求卡在哪：
    线程用满 → 看连接池是否也用满、排队等连接的线程有多少；堆不够用 → 看全量 GC 和老年代存活对象"""
    return ['### 线程、连接池与 GC（测试窗口内）', '', table([
        ['nilo-web Tomcat 忙碌线程 峰值 / 上限', prom_pair(prom, 'tomcat_threads_busy_max', 'tomcat_threads_limit')],
        ['nilo-web Tomcat 连接数峰值（含排队等线程的连接）', prom_line(prom, 'tomcat_connections_max')],
        ['数据库连接池 使用中峰值 / 上限', prom_pair(prom, 'hikari_active_max', 'hikari_limit')],
        ['数据库连接池 排队等连接的线程数峰值', prom_line(prom, 'hikari_pending_max')],
        ['数据库连接池 平均等待拿到连接（ms）', prom_line(prom, 'hikari_acquire_avg_ms')],
        ['数据库连接池 平均每次占用连接（ms）', prom_line(prom, 'hikari_usage_avg_ms')],
        ['小 GC 次数 / 停顿（秒）', prom_pair(prom, 'gc_minor_count', 'gc_minor_seconds')],
        ['全量 GC 次数 / 停顿（秒）', prom_pair(prom, 'gc_major_count', 'gc_major_seconds')],
        ['老年代存活对象峰值 / 老年代上限（MiB）', prom_pair(prom, 'old_gen_live_max_mib', 'old_gen_limit_mib')],
        ['平均每秒分配内存（MiB）', prom_line(prom, 'alloc_mib_per_s')],
    ], ['指标', '值']), '']


def redis_commands_section(prom: dict, accepted: int | None) -> list[str]:
    """Redis 按命令类型的执行次数，从高到低排列，并折算成平均每个放行请求几条。没有数据时不输出这一部分。
    accepted：k6 统计的放行请求数"""
    counts = prom_values(prom, 'redis_commands_by_cmd')
    if not counts:
        return []
    # 只保留窗口内执行过的命令（increase 是估算值，不足 1 次的视为没执行）；按次数从高到低排序
    used = sorted(((cmd, n) for cmd, n in counts.items() if round(n) > 0), key=lambda kv: -kv[1])
    rows = [[cmd, round(n), fmt(n / accepted, 2) if accepted else '-'] for cmd, n in used]
    return ['### Redis 命令分布（测试窗口内）', '',
            table(rows, ['命令', '次数', '平均每个放行请求（条）']), '',
            '- Lua 脚本里调用的命令会按各自的名字再计一次：脚本执行一次记一次 evalsha，脚本里的每条命令也各记一次',
            '- info 主要来自被测机的写入监控（每秒 10 次）和 redis_exporter，是测量本身的开销，不是业务',
            '- 测试窗口包含压测结束后的排空阶段，这段时间的后台命令也计算在内，所以每个请求的条数略微偏高',
            '']


def measurement_section(sut: dict, load_cpu: dict | None, net: str, hosts: dict[str, dict[str, str]]) -> list[str]:
    """测量本身的可信度：监控查询有没有跟上 0.1 秒的节拍、压测机 CPU、两台机器的配置和之间的网络。
    hosts：{'被测机': {'cpu': ..., 'nproc': ...}, '压测机': {...}}"""
    mon = sut.get('monitor', {})
    # 每台机器一段，如 "被测机 AMD EPYC 7763 64-Core Processor，4 核"；文件缺失时显示 -
    host_text = '；'.join(f'{name} {h.get("cpu", "-")}，{h.get("nproc", "-")} 核' for name, h in hosts.items())
    return ['### 测量本身', '',
            f'- 机器配置：{host_text}。每次分到的云主机硬件可能不同，型号不同的两次结果不宜直接比较',
            f'- 写入监控共查询 {mon.get("polls", "-")} 次，单次平均 {mon.get("avg_poll_ms", "-")} ms，最长 {mon.get("max_poll_ms", "-")} ms',
            '- 写入完成延迟 = 写入停止时刻（被测机）− 压测结束时刻（压测机收到最后一个响应），两台机器均通过 NTP 对时',
            '- 写入停止时刻取第一次查到最终数据的那次查询结束的时间，只会比真正写完偏晚、不会偏早；'
            '误差约为查询间隔 0.1 秒加单次查询耗时（单次查询耗时见第一条）',
            # 相邻的几个字符串会自动拼成一个，用来把长句拆成多行书写
            f'- 压测机 CPU 在压测期间每秒采样一次，共 {load_cpu["seconds"] if load_cpu else "-"} 秒，'
            f'被云主机宿主占用（steal）平均 {fmt(load_cpu["steal_avg"]) if load_cpu else "-"}%；'
            f'CPU 使用率的 P95（把每秒的采样从低到高排列，取第 95 百分位）达到 {LOAD_CPU_LIMIT:.0f}%，即判定压测机可能成为瓶颈',
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


HOST_SERVICES = {
    '/system.slice/nginx-perf.service': 'nginx（宿主机）',
    '/system.slice/docker.service': 'dockerd（宿主机，含端口转发）',
}


def host_values(prom: dict, name: str) -> dict[str, float]:
    """取宿主机服务的一项指标，把 cgroup 路径换成 HOST_SERVICES 里的可读名字；没有数据时返回空字典"""
    v = prom_values(prom, name) or {}
    return {HOST_SERVICES.get(k, k): x for k, x in v.items()}


def prom_pair(prom: dict, first: str, second: str) -> str:
    """把两项指标按分组配对成一行，如 "nilo-web: 200.0 / 200.0, nilo-mq-consumer: 3.0 / 10.0"（峰值 / 上限、次数 / 秒数）；
    以第一项的分组为准，第二项缺失的显示 -；第一项没有数据时整行显示 -"""
    a = prom_values(prom, first)
    if not a:
        return '-'
    b = prom_values(prom, second) or {}
    return ', '.join(f'{k}: {fmt(x)} / {fmt(b.get(k))}' for k, x in a.items())


def prom_value(prom: dict, name: str) -> float | None:
    """取汇总成一个数的指标（查询结果没有分组，分组名为 value，如服务端响应时间）；没有数据时返回 None"""
    v = prom_values(prom, name)
    return v.get('value') if v else None


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


def read_host(path: str) -> dict[str, str]:
    """读取机器配置文件里的 "cpu: ..." 和 "nproc: ..." 两行，返回 {'cpu': 型号, 'nproc': 核数}；文件缺失时返回空字典"""
    host = {}
    for line in read_text(path).splitlines():
        key, sep, value = line.partition(':')  # 按第一个冒号分成三段：键、冒号、值
        if sep and key in ('cpu', 'nproc') and value.strip():
            host[key] = value.strip()
    return host


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
    # 格子里的 | 会被当成列分隔符（如 Redis 命令 client|setname），转义成 \| 才会原样显示
    lines += ['| ' + ' | '.join(fmt(c).replace('|', '\\|') for c in r) + ' |' for r in rows]
    return '\n'.join(lines)


if __name__ == '__main__':
    main()
