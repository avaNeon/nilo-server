#!/usr/bin/env bash
# 压测机：配合被测机逐轮测试，每轮测一个版本。工作流的 "Run rounds" 步骤在仓库根目录运行它。
# 用法：bash docker/integration/perf/load/run_rounds.sh（参数都从环境变量读取，见下方）
#
# 压测机不需要知道要测哪些版本：每轮开始前问被测机（GET /verify-ready），被测机告诉它这是第几轮、测哪个版本、是不是最后一轮。
# 每一轮的结果写到 perf-load/round-<轮次>：
#   ① 等被测机换好这一轮的版本
#   ② 预热，等预热产生的写入排空
#   ③ 通知被测机记下起点，开始压测；压测期间每秒记录一次压测机的 CPU
#   ④ 通知被测机压测结束，等它判定写入停止、对账，取回结果
#   ⑤ 从 Prometheus 汇总这一轮测试窗口内的指标
# Prometheus 全程运行，每轮按自己的起止时刻取数据。
#
# 环境变量（工作流设置）：
#   SUT_IP、SIGNAL_PORT                被测机的隧道地址、信号服务的端口
#   RATE、DURATION                     正式压测的每秒请求数、时长
#   WARMUP_RATE、WARMUP_DURATION       预热的每秒请求数、时长
#   SEED_COUNT、VIDEO_ID_BASE、DISTRIBUTION  种子视频数量、起始 ID、视频分布
#   DIAGNOSE                           true 时每轮拍 3 张 nilo-web 的堆快照，存到 diag/round-<轮次>

# -e：命令出错就退出；-u：用到未设置的变量就报错；-o pipefail：管道里任何一个命令出错都算出错
set -euo pipefail

LOAD_DIR=docker/integration/perf/load
SIGNAL=http://$SUT_IP:$SIGNAL_PORT

main() {
  local n=1 out
  while true; do
    out=perf-load/round-$n
    mkdir -p "$out"
    wait_ready "$n" "$out"
    # ::group:: 让这一轮的日志在 GitHub Actions 页面上折叠成一组
    echo "::group::第 $n 轮：$(jq -r .version "$out/round.json")"
    run_round "$n" "$out"
    echo "::endgroup::"
    if [ "$(jq -r .last "$out/round.json")" = true ]; then
      break
    fi
    n=$((n + 1))
  done
}

# 一轮测量的 ②～⑤。$1：轮次，$2：这一轮的结果目录
run_round() {
  local out=$2 start end

  # ② 预热，然后等预热产生的写入排空再记录起点：V1 及以后每 5 秒才把本地队列发往 MQ
  k6_run "$WARMUP_RATE" "$WARMUP_DURATION" "$out/k6-warmup.json" -q
  sleep 20

  # ③ 通知被测机：要开始压测了，记下现在的数据作为起点，开始监控写入
  curl -sf -X POST "$SIGNAL/start-monitor" | tee "$out/baseline.json"
  echo
  start=$(date +%s)
  load_test "$1" "$out"

  # ④ 通知被测机：压测结束了，附带结束时刻和放行请求数，被测机据此判断写入何时停止并对账
  curl -sf -X POST -H 'Content-Type: application/json' --data @"$out/k6-main-brief.json" "$SIGNAL/load-finished"
  echo
  # 诊断模式：k6 已退出、连接全部断开后再拍一张，和压测中的两张对比，差出来的就是压力带来的占用（连接、排队的请求等）。
  # 必须在索要结果之前拍：被测机交出结果后，很快就会停掉这一轮的 nilo-web
  if [ "$DIAGNOSE" = true ]; then
    sleep 10
    heap_dump "$1" after-load
  fi
  wait_result "$out"
  end=$(date +%s)

  # ⑤ 汇总测试窗口（起点 → 拿到结果）内的指标；Prometheus 在等结果期间继续采集，覆盖了排空过程
  python3 "$LOAD_DIR/summarize_metrics.py" --start "$start" --end "$end" --out "$out/prom-summary.json" \
    || echo "::warning::第 $1 轮的 Prometheus 指标汇总失败"
}

# ① 反复询问被测机：第 N 轮准备好了吗？轮次对上才算就绪，返回内容存到 round.json。
# 被测机要停服务、等 ES 同步、重建容器，第一轮之前还要启动整个环境，最多等 40 分钟。$1：轮次，$2：这一轮的结果目录
wait_ready() {
  local info
  for _ in $(seq 1 240); do
    # 返回形如 {"ready": true, "round": 2, "version": "perf-v1", "last": false}
    if info=$(curl -sf -m 3 "$SIGNAL/verify-ready") && [ "$(jq -r .round <<< "$info")" = "$1" ]; then
      echo "$info" > "$2/round.json"
      return 0
    fi
    sleep 10
  done
  echo "被测机第 $1 轮未就绪" >&2
  return 1
}

# 正式压测，同时每秒记录一次压测机的 CPU，用来判断瓶颈是不是压测机自己。$1：轮次，$2：这一轮的结果目录
load_test() {
  local sampler dumper=''
  python3 docker/integration/perf/sample_cpu.py --out "$2/load-cpu.csv" &
  sampler=$!
  # 诊断模式：压测开始后第 60 秒、第 150 秒各拍一张堆快照。
  # Spring 的 heapdump 端点会先做一次全量 GC，快照里只剩存活对象，即真正占着堆的东西
  if [ "$DIAGNOSE" = true ]; then
    local t0
    t0=$(date +%s)
    (
      sleep 60
      heap_dump "$1" during-060s
      left=$(( t0 + 150 - $(date +%s) ))
      if [ "$left" -gt 0 ]; then sleep "$left"; fi
      heap_dump "$1" during-150s
    ) &
    dumper=$!
  fi
  k6_run "$RATE" "$DURATION" "$2/k6-main.json"
  # 采样进程万一已经退出，kill 会失败；不能因此中断后面的轮次
  kill "$sampler" 2>/dev/null || true
  # 等后台的快照下载完
  if [ -n "$dumper" ]; then wait "$dumper"; fi
}

# 运行 k6。$1：每秒请求数，$2：时长，$3：结果文件（同时生成同名的 -brief.json），之后的参数原样交给 k6
k6_run() {
  k6 run "${@:4}" \
    -e TARGET="http://$SUT_IP:7071" -e RATE="$1" -e DURATION="$2" \
    -e VIDEO_COUNT="$SEED_COUNT" -e VIDEO_ID_BASE="$VIDEO_ID_BASE" -e DISTRIBUTION="$DISTRIBUTION" \
    -e SUMMARY_FILE="$3" \
    "$LOAD_DIR/load.js"
}

# 拍一张 nilo-web 的堆快照，存到 diag/round-<轮次>/heap-<名字>.hprof；失败只提醒。$1：轮次，$2：名字
heap_dump() {
  mkdir -p "diag/round-$1"
  curl -sf -m 180 -o "diag/round-$1/heap-$2.hprof" "http://$SUT_IP:7071/actuator/heapdump" \
    || echo "第 $1 轮的堆快照 $2 下载失败" >&2
}

# 反复索要结果，直到被测机判定写入停止、对账完成。被测机最多等排空 30 分钟，这里最多等约 33 分钟。$1：这一轮的结果目录
wait_result() {
  local code
  for _ in $(seq 1 200); do
    code=$(curl -s -m 5 -o "$1/sut-result.json" -w '%{http_code}' "$SIGNAL/result" || true)
    if [ "$code" = 200 ]; then return 0; fi
    sleep 10
  done
  echo "::warning::没有等到被测机的结果"
}

main "$@"
