"""把压测报告（GitHub Actions 摘要页保存下来的 Markdown）转成 JSON，供压测结果页面（site/test/perf-playCount）读取。
用法：python report_to_json.py --out ../../../site/test/perf-playCount/data 报告1.md [报告2.md ...]

每份报告是一次运行，写成 <out>/runs/<运行编号>.json；运行编号就是 GitHub Actions 的 run id，页面据此链接回那次运行。
同一份报告重复转换会覆盖原文件。最后重写 <out>/index.json：runs 目录下所有运行的文件名，按测试时间排序。
页面部署在静态托管上，没法列出目录里有哪些文件，所以要靠 index.json 告诉它。

只读取报告里"各轮对比"表、各轮的"被测机资源"表与"线程、连接池与 GC"表，以及测量方法部分，派生的数（如吞吐、相对第一轮的倍数）由页面计算。
"""
# 标准库
import argparse
import json
import math
import os
import re
import statistics

REPO = 'avaNeon/nilo-server'


def main() -> None:
    """主流程：逐份读取报告 → 各写成一个 JSON → 重写索引"""
    p = argparse.ArgumentParser()
    p.add_argument('--out', required=True)
    p.add_argument('reports', nargs='+')
    a = p.parse_args()
    runs_dir = os.path.join(a.out, 'runs')
    os.makedirs(runs_dir, exist_ok=True)

    for path in a.reports:
        run = read_report(path)
        # ensure_ascii=False 让中文原样写出；indent 让文件便于在 git 里比较差异
        with open(os.path.join(runs_dir, f'{run["id"]}.json'), 'w', encoding='utf-8') as f:
            json.dump(run, f, ensure_ascii=False, indent=2)
        print(f'{path} → runs/{run["id"]}.json（{len(run["rounds"])} 轮）')
    write_index(a.out, runs_dir)


def write_index(out: str, runs_dir: str) -> None:
    """重写 index.json：runs 目录下所有运行的文件名，按测试时间从早到晚排序"""
    runs = []
    for name in os.listdir(runs_dir):
        if name.endswith('.json'):
            with open(os.path.join(runs_dir, name), encoding='utf-8') as f:
                runs.append((json.load(f).get('date') or '', name))
    with open(os.path.join(out, 'index.json'), 'w', encoding='utf-8') as f:
        json.dump({'runs': [name for _, name in sorted(runs)]}, f, ensure_ascii=False, indent=2)


# ---------------- 读取一份报告 ----------------

def read_report(path: str) -> dict:
    """读取一份报告，返回这次运行的数据：运行编号、测试条件、两台机器、网络，以及每一轮的指标"""
    with open(path, encoding='utf-8') as f:
        text = f.read()
    _, compare = parse_table(text, '### 各轮对比')
    names = parse_table(text, '### 各轮对比')[0][1:]  # 表头去掉第一列"指标"，剩下的是各轮的名字
    # 各轮的详细结果：{轮次名: 那一段的文字}
    details = dict(re.findall(r'<details><summary>(第 \d+ 轮.*?) 的详细结果</summary>(.*?)</details>', text, re.S))
    first = details.get(names[0], '')
    _, k6 = parse_table(first, '### 压测（k6）')
    _, conclusion = parse_table(first, '### 结论')
    run_id = re.search(r'nilo-sut-(\d+)-\d+', text)  # 被测机在 Tailscale 里的名字带着 run id，见工作流的 SUT_NAME
    sut, load = machines(text)
    rtt, relay = network(text)
    return {
        'id': run_id.group(1) if run_id else os.path.splitext(os.path.basename(path))[0],
        'url': f'https://github.com/{REPO}/actions/runs/{run_id.group(1)}' if run_id else None,
        'title': re.search(r'^## (.+)$', text, re.M).group(1),
        # 第一轮压测结束的时刻，用来给各次运行排序
        'date': first_value(conclusion, '压测结束时刻（压测机收到最后一个响应）'),
        'rate': num(first_value(k6, '每秒请求数（目标）')),
        'duration': first_value(k6, '持续时间'),
        'duration_s': parse_duration(first_value(k6, '持续时间') or ''),
        'distribution': first_value(k6, '视频分布'),
        'sut': sut,
        'load': load,
        'rtt_ms': rtt,
        'relay': relay,
        'rounds': [read_round(i, name, compare, details.get(name, '')) for i, name in enumerate(names)],
    }


def read_round(i: int, name: str, compare: dict[str, list[str]], detail: str) -> dict:
    """一轮的指标：各轮对比表里第 i 列的数，加上这一轮详细结果里每个容器的每请求 CPU"""
    def col(key: str, part: int | None = None) -> float | None:
        """对比表里 key 那一行第 i 列的数；part 不为 None 时先按 / 拆开取第 part 段"""
        values = compare.get(key)
        return num(values[i] if values and i < len(values) else None, part)

    reconcile = (compare.get('对账') or [''] * (i + 1))[i]
    return {
        'n': int(re.match(r'第 (\d+) 轮', name).group(1)),
        'version': re.sub(r'^第 \d+ 轮 ', '', name).replace('（对照）', ''),
        'control': '对照' in name,
        'reconcile_ok': reconcile.startswith('通过'),
        'drain_timed_out': '排空超时' in reconcile,
        'accepted': col('放行 / 被限流 / 失败', 0),
        'rejected': col('放行 / 被限流 / 失败', 1),
        'failed': col('放行 / 被限流 / 失败', 2),
        'failed_502': col('其中 nginx 返回的 502'),
        'dropped': col('未能按时发出的请求（dropped）'),
        'accepted_latency_ms': {'avg': col('放行请求的响应时间 平均 / P99（ms）', 0),
                                'p99': col('放行请求的响应时间 平均 / P99（ms）', 1)},
        'server_latency_ms': {'avg': col('服务端响应时间 平均 / P99（ms）', 0),
                              'p99': col('服务端响应时间 平均 / P99（ms）', 1)},
        'write_complete_latency_s': col('写入完成延迟（秒）'),
        'queue_ready_max': col('播放量队列积压峰值（待消费 / 处理中）', 0),
        'queue_unacked_max': col('播放量队列积压峰值（待消费 / 处理中）', 1),
        'mysql_rows_updated_per_s_max': col('MySQL 每秒更新行数峰值'),
        'mysql_queries_per_s_max': col('MySQL 每秒查询数峰值'),
        'redis_commands_per_s_max': col('Redis 每秒命令数峰值'),
        'sut_cpu_pct': {'avg': col('被测机整机 CPU 使用率 平均 / P95（%，压测期间）', 0),
                        'p95': col('被测机整机 CPU 使用率 平均 / P95（%，压测期间）', 1)},
        'load_cpu_p95_pct': col('压测机 CPU 使用率 P95（%）'),
        'web_full_gc': col('nilo-web 全量 GC 次数'),
        'cpu_ms_per_request': container_cpu(detail),
        **thread_and_pool(detail),
    }


def container_cpu(detail: str) -> dict[str, float]:
    """一轮详细结果里"被测机资源"表的每请求 CPU：{容器或宿主机服务: 毫秒}，包含所有容器，不只是对比表里列出的那几个"""
    header, rows = parse_table(detail, '### 被测机资源')
    if not header:
        return {}
    col = next(i for i, h in enumerate(header) if '每个放行请求' in h) - 1  # 减去第一列（容器名）
    return {name: v for name, values in rows.items() if (v := num(values[col])) is not None}


def thread_and_pool(detail: str) -> dict[str, float | None]:
    """一轮详细结果里"线程、连接池与 GC"表的数：nilo-web 的 Tomcat 线程和数据库连接池，以及 nilo-mq-consumer 占用数据库连接的时间。
    表里每个指标的值是"服务名: 数, 服务名: 数"，这里按服务名取"""
    _, rows = parse_table(detail, '### 线程、连接池与 GC')

    def of(key: str, service: str, part: int | None = None) -> float | None:
        cell = (rows.get(key) or [''])[0]
        m = re.search(re.escape(service) + r': ([^,]+)', cell)
        return num(m.group(1), part) if m else None

    return {
        'tomcat_busy_max': of('nilo-web Tomcat 忙碌线程 峰值 / 上限', 'nilo-web', 0),
        'tomcat_threads_limit': of('nilo-web Tomcat 忙碌线程 峰值 / 上限', 'nilo-web', 1),
        'db_pool_limit': of('数据库连接池 使用中峰值 / 上限', 'nilo-web', 1),
        'db_pool_waiting_max': of('数据库连接池 排队等连接的线程数峰值', 'nilo-web'),
        'db_pool_wait_ms': of('数据库连接池 平均等待拿到连接（ms）', 'nilo-web'),
        'db_pool_hold_ms': of('数据库连接池 平均每次占用连接（ms）', 'nilo-web'),
        'consumer_db_hold_ms': of('数据库连接池 平均每次占用连接（ms）', 'nilo-mq-consumer'),
    }


def machines(text: str) -> tuple[dict, dict]:
    """测量方法部分的机器配置，返回 (被测机, 压测机)，各为 {'cpu': 型号, 'nproc': 核数}"""
    found = {}
    for role, cpu, nproc in re.findall(r'(被测机|压测机) (.+?)，(\d+) 核', text):
        found.setdefault(role, {'cpu': cpu, 'nproc': int(nproc)})
    return found.get('被测机', {}), found.get('压测机', {})


def network(text: str) -> tuple[float | None, bool]:
    """tailscale ping 的往返时间中位数（毫秒），以及是否经中继（DERP）转发而不是直连"""
    times = [float(t) for t in re.findall(r'^pong from .* in (\d+(?:\.\d+)?)ms', text, re.M)]
    relay = bool(re.search(r'^pong from .* via DERP', text, re.M))
    return (statistics.median(times) if times else None), relay


# ---------------- Markdown 表格与数字 ----------------

def parse_table(text: str, heading: str) -> tuple[list[str], dict[str, list[str]]]:
    """取标题 heading 之后的第一张 Markdown 表格，返回 (表头, {第一列: 其余各列})；找不到时返回空表"""
    start = text.find(heading)
    if start < 0:
        return [], {}
    lines = []
    for line in text[start + len(heading):].splitlines():
        if line.startswith('|'):
            lines.append(line)
        elif lines:  # 表格结束
            break
    # 格子里转义过的 \| 先换成别的字符，免得被当成分隔符；按 | 拆开、去掉首尾空白；跳过 |---|---| 这样的分隔行
    cells = [[c.strip() for c in line.replace('\\|', '¦').strip('|').split('|')] for line in lines]
    cells = [row for row in cells if not all(set(c) <= set('-: ') for c in row)]
    if not cells:
        return [], {}
    return cells[0], {row[0]: row[1:] for row in cells[1:]}


def first_value(rows: dict[str, list[str]], key: str) -> str | None:
    """两列表格（项目 | 值）里 key 那一行的值"""
    values = rows.get(key)
    return values[0] if values else None


def num(text: str | None, part: int | None = None) -> float | None:
    """把格子里的文字转成数字。part 不为 None 时，先按 / 拆开取第 part 段（如 "173.92 / 839.53"）。
    "-"、"nan" 等不是有效数字的返回 None"""
    if text is None:
        return None
    if part is not None:
        pieces = text.split('/')
        text = pieces[part] if part < len(pieces) else ''
    try:
        v = float(text.replace(',', '').strip())
    except ValueError:
        return None
    if math.isnan(v):
        return None
    return int(v) if v.is_integer() else v  # 整数写成整数，JSON 里不出现 182878.0


def parse_duration(text: str) -> float | None:
    """k6 的时长写法转成秒，如 3m → 180、1m30s → 90"""
    units = {'h': 3600, 'm': 60, 's': 1}
    parts = re.findall(r'(\d+(?:\.\d+)?)(h|m|s)', text)
    total = sum(float(n) * units[u] for n, u in parts)
    if not total:
        return None
    return int(total) if total.is_integer() else total


if __name__ == '__main__':
    main()
