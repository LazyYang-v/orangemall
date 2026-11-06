#!/usr/bin/env bash
# 用 docker 版 k6（无需本机安装）在 compose 内网跑压测脚本，结果摘要写入 bench/results/。
# 用法：bench/run.sh <script.js> [K=V ...]
#   bench/run.sh sessions.js RATE=1500
#   bench/run.sh submit-reject.js RATE=3000 USERS=2000
#   bench/run.sh seckill-e2e.js AID=S...-100501 USERS=5000 VUS=1000
# BASE 默认 http://mall-gateway:8080/api（直连网关）；测 nginx 入口用 BASE=http://nginx/api（会受 seckill_ip 限流）。
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
SCRIPT=${1:?脚本名，如 sessions.js}; shift
K6_IMAGE=${K6_IMAGE:-grafana/k6:0.54.0}
NET=${NET:-orange-mall_default}
STAMP=$(date +%Y%m%d-%H%M%S)
mkdir -p "$HERE/results/raw"
ENVS=()
for kv in "$@"; do ENVS+=(-e "$kv"); done
docker run --rm -i --network "$NET" ${ENVS[@]+"${ENVS[@]}"} \
  -v "$HERE/k6:/scripts:ro" -v "$HERE/results/raw:/out" \
  --ulimit nofile=65535:65535 \
  "$K6_IMAGE" run --quiet --summary-export "/out/${SCRIPT%.js}-$STAMP.json" "/scripts/$SCRIPT" \
  2>&1 | tee "$HERE/results/raw/${SCRIPT%.js}-$STAMP.txt"
