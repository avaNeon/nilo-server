#!/usr/bin/env bash
# 被测机：在同一台机器上依次测试多个版本，每个版本一轮。工作流的 "Run rounds" 步骤在本目录运行它。
# 用法：bash run_rounds.sh（参数都从环境变量读取，见下方）
#
# 为什么放在一次运行里：每次运行分到的云主机 CPU 型号、两台机器之间的网络都可能不同，分开跑的版本之间没法直接比较；
# 同一次运行里各版本用的是同一台机器、同一条网络，比较才公平。
# 版本不止一个时，最后再测一遍第一个版本作对照：同一版本前后两轮的差距，反映先后顺序和机器波动带来的影响，
# 版本之间比这个差距还小的差异，不宜下结论。
#
# 每一轮：
#   ① 换上这一轮的版本：停掉 nilo-web、nilo-mq-consumer，清空播放量队列，等 ES 同步完，再用这一轮的镜像重新创建这两个容器
#   ② 运行信号服务 serve.py，配合压测机完成这一轮的测量，结果写到 $RESULTS_DIR/round-<轮次>
#   ③ 保存这一轮 nilo-web、nilo-mq-consumer 的日志：下一轮重新创建容器后，旧容器的日志就没了
# 第一轮也同样重新创建：每一轮都从刚启动的服务开始，条件一致。
# 中间件和 nilo-canal-client 全程不重启，canal-client 一直是第一个版本的镜像：perf-v0 ~ perf-v3 的 canal-client 代码相同。
# 对账只看这一轮起点之后的增量，所以不用重新灌数据。
#
# 环境变量（工作流设置）：
#   VERSIONS            被测版本（镜像标签），英文逗号分隔
#   DIAGNOSE            true 时 nilo-web 全程 JFR 录制，每轮一个文件
#   PYTHON              装好依赖的 Python
#   RESULTS_DIR         结果目录
#   SIGNAL_PORT         信号服务的端口
#   BIND_IP、NILO_WEB_HEAP、NILO_WEB_MEM_LIMIT  重新创建容器时 docker-compose.yaml 要用到，必须与启动环境时一致
#
# 退出码：每一轮都对账通过为 0，否则为 1。某一轮对账不通过不影响后面的轮次继续测

# -e：命令出错就退出；-u：用到未设置的变量就报错；-o pipefail：管道里任何一个命令出错都算出错
set -euo pipefail

COMPOSE_FILE=../../docker-compose.yaml

main() {
  local versions rounds failed=0
  IFS=, read -ra versions <<< "$VERSIONS"   # 按逗号拆成数组
  rounds=("${versions[@]}")
  if [ "${#versions[@]}" -gt 1 ]; then
    rounds+=("${versions[0]}")               # 最后再测一遍第一个版本作对照
  fi

  local i n version out last
  for i in "${!rounds[@]}"; do
    n=$((i + 1))
    version=${rounds[$i]}
    out=$RESULTS_DIR/round-$n
    last=()
    if [ "$n" -eq "${#rounds[@]}" ]; then last=(--last); fi
    # ::group:: 让这一轮的日志在 GitHub Actions 页面上折叠成一组
    echo "::group::第 $n 轮：$version"
    switch_version "$version" "$n"
    $PYTHON serve.py --bind "$BIND_IP" --port "$SIGNAL_PORT" --out "$out" --round "$n" --version "$version" "${last[@]}" || failed=1
    save_logs "$out"
    echo "::endgroup::"
  done
  exit "$failed"
}

# ① 换上这一轮的版本。$1：版本，$2：轮次
switch_version() {
  # 停掉上一轮的服务，此后不会再有播放量写入。nilo-web 正常停止：诊断模式下 JVM 退出时才写出上一轮的 JFR 录制，最多等 2 分钟
  docker stop -t 120 nilo-web nilo-mq-consumer
  # 上一轮排空超时时，队列里还有没消费的消息，清掉，免得算进这一轮
  docker exec rabbit rabbitmqctl purge_queue video.play-count.queue || true
  # 等 ES 同步完上一轮的写入；超时只提醒，照常继续
  $PYTHON wait_for_es_sync.py --play-count || echo "::warning::第 $2 轮开始前 ES 没有同步完，Canal 的同步开销会延续到这一轮"

  export NILO_TAG=$1
  NILO_WEB_DIAG_OPTS=$(diag_opts "$2")
  export NILO_WEB_DIAG_OPTS
  # --no-deps：只重建这两个，不碰它们依赖的服务；--force-recreate：镜像没变（第一轮、对照轮）也重新创建；
  # --wait：等到容器健康才返回。先启动消费端：它负责声明队列，web 发出的消息才不会丢
  local svc
  for svc in nilo-mq-consumer nilo-web; do
    docker compose -f "$COMPOSE_FILE" up -d --no-deps --force-recreate --wait --wait-timeout 300 "$svc"
  done
  # 确认 nginx 能转发到新的 nilo-web：nginx 连接池里指向旧容器的连接都断了，要重新建立
  for _ in $(seq 1 30); do
    curl -sf -o /dev/null "http://$BIND_IP:7071/actuator/info" && return 0
    sleep 1
  done
  echo "nginx 未能转发到第 $2 轮的 nilo-web" >&2
  return 1
}

# 诊断模式下 nilo-web 的 JFR 录制参数：启动即开始录制，JVM 退出时写出到 /diag/nilo-web-round-<轮次>.jfr（挂载到 docker/integration/diag），
# 并记录老年代抽样对象到 GC 根的引用路径；录制最多占 500 MB 磁盘，超出时丢弃最早的部分。不是诊断模式时输出为空。$1：轮次
diag_opts() {
  if [ "$DIAGNOSE" = true ]; then
    echo "-XX:StartFlightRecording=settings=profile,disk=true,maxsize=500m,dumponexit=true,path-to-gc-roots=true,filename=/diag/nilo-web-round-$1.jfr"
  fi
}

# ③ 保存这一轮两个服务的日志。$1：这一轮的结果目录
save_logs() {
  mkdir -p "$1/logs"
  local c
  for c in nilo-web nilo-mq-consumer; do
    docker logs "$c" > "$1/logs/$c.log" 2>&1 || true
  done
}

main "$@"
