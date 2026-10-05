"""压测机 CPU 采样：每秒读一次 /proc/stat，写成 CSV，直到被终止。
用来判断压测机自己是否成为瓶颈：CPU 接近跑满时，k6 发请求、读响应都要排队等 CPU，测出的响应时间会偏大，吞吐也上不去。
用法：python sample_cpu.py --out load-cpu.csv &（工作流在正式压测开始前放到后台运行，压测结束后终止它）

计算方法：
  /proc/stat 是 Linux 内核提供的虚拟文件，记录了开机以来 CPU 在各种状态下累计花了多少时间（用户程序、内核、空闲……）。
  这些值只增不减，所以每隔 1 秒读一次，两次相减，就是这 1 秒内花在各个状态上的时间，再除以总时间得到百分比。
  这和 Prometheus 对计数器求 rate 是同一个道理。

输出的每一列（百分比，所有核合计）：
  busy_pct     忙碌：除空闲以外的全部时间
  user_pct     用户程序，k6 的大部分开销在这里
  system_pct   内核
  softirq_pct  网络收发包的处理，Tailscale 隧道的加密解密开销也主要体现在这里和 user 里
  steal_pct    被云主机的宿主机拿走、本机用不上的时间。云主机的"邻居"抢资源时会升高
"""
# 标准库
import argparse
import signal
import sys
import time


def main() -> None:
    """主流程：每秒采样一次 CPU，写一行 CSV，直到工作流用 kill 终止它"""
    p = argparse.ArgumentParser()
    p.add_argument('--out', required=True)
    p.add_argument('--interval', type=float, default=1.0)
    a = p.parse_args()
    # 工作流用 kill 结束这个脚本时，进程会收到 SIGTERM 信号：收到就正常退出，下面 with 打开的文件会被正常关闭
    signal.signal(signal.SIGTERM, lambda *_: sys.exit(0))

    # buffering=1：按行缓冲，每写完一行就落到文件里。即使进程被强制杀掉，已经写的数据也不会丢
    with open(a.out, 'w', buffering=1) as f:
        f.write('time,busy_pct,user_pct,system_pct,softirq_pct,steal_pct\n')
        prev = read_cpu()
        next_tick = time.time()          # 下一次采样的预定时间
        while True:
            # 按固定节拍采样：在上一次的预定时间上加 1 秒，而不是写完再睡 1 秒，避免间隔越拉越长
            next_tick += a.interval
            time.sleep(max(0.0, next_tick - time.time()))
            cur = read_cpu()
            # 对每一类，用这次的累计值减去上次的，得到这 1 秒内的增量
            d = {k: cur[k] - prev[k] for k in cur}
            prev = cur
            # 这 1 秒的总时间；"or 1" 表示总和为 0 时用 1 代替，防止下面除以 0
            total = sum(d.values()) or 1
            pct = {k: 100.0 * v / total for k, v in d.items()}
            # 忙碌 = 除空闲以外的全部时间；steal 也算进去，因为这部分 CPU 本机同样用不上
            f.write(f'{time.time():.3f},{100.0 - pct["idle"]:.1f},{pct["user"]:.1f},{pct["system"]:.1f},'
                    f'{pct["softirq"]:.1f},{pct["steal"]:.1f}\n')


def read_cpu() -> dict[str, int]:
    """main() 每秒调用一次：读取 /proc/stat 第一行，返回各状态的累计时间（单位：时钟滴答）"""
    # 第一行是所有核的合计，形如：cpu  4705 150 1120 16250 520 0 30 12 0 0
    # 各列依次是：user nice system idle iowait irq softirq steal guest guest_nice。
    # guest、guest_nice 已经包含在 user、nice 里，不再重复计入，所以只取前 8 个数（[1:9] 跳过开头的 "cpu"）
    with open('/proc/stat') as f:
        user, nice, system, idle, iowait, irq, softirq, steal = (int(x) for x in f.readline().split()[1:9])
    # 合并成 5 类：nice 是低优先级的用户程序，算进 user；irq 是硬件中断，算进 system；iowait 是等待磁盘的空闲时间，算进 idle
    return {
        'user': user + nice,
        'system': system + irq,
        'softirq': softirq,
        'steal': steal,
        'idle': idle + iowait,
    }


if __name__ == '__main__':
    main()
