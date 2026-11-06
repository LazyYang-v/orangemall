#!/usr/bin/env bash
# 落单链路剖析：积压 → 恢复消费 → 落单途中对 seckill / order 各抓 2 次线程快照（kill -3，输出在容器 stdout）。
# 用法：bench/profile-drain.sh <activityId> [stock=1000]；快照写到 bench/results/raw/dump-{seckill,order}-<时间>.txt
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
AID=${1:?activityId 必填}; STOCK=${2:-1000}
source "$HERE/drills/common.sh"
bash "$HERE/prepare.sh" "$AID" "$STOCK" | tail -1
api POST /admin/seckill/consumer/pause >/dev/null
trap 'api POST /admin/seckill/consumer/resume >/dev/null' EXIT
bash "$HERE/run.sh" seckill-e2e.js AID="$AID" USERS=5000 VUS=1000 POLL=0 | grep -E "orders_queued" || true
T=$(date -u +%Y-%m-%dT%H:%M:%SZ); TAG=$(date +%H%M%S)
api POST /admin/seckill/consumer/resume >/dev/null
for n in 1 2; do
  sleep 1.5
  docker exec orange-mall-mall-seckill-1 kill -3 1
  docker exec orange-mall-mall-order-1 kill -3 1
done
for _ in $(seq 1 300); do [ "$(rc GET "seckill:inflight:$AID")" = "0" ] && break; sleep 0.5; done
OUT="$HERE/results/raw"
docker logs --since "$T" orange-mall-mall-seckill-1 > "$OUT/dump-seckill-$TAG.txt" 2>&1
docker logs --since "$T" orange-mall-mall-order-1 > "$OUT/dump-order-$TAG.txt" 2>&1
echo "$OUT/dump-seckill-$TAG.txt $OUT/dump-order-$TAG.txt"
bash "$HERE/verify.sh" "$AID" | tail -1
