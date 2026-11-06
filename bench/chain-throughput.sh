#!/usr/bin/env bash
# 落单链路纯吞吐（不含 k6 登录/轮询噪声）：
#   暂停主消费者 → 5000 用户抢购（合格订单全部积压在 Kafka）→ 恢复消费者 → 计时直到在途归零
#   吞吐 = 成交数 / 恢复到在途归零的耗时；结束后执行 verify.sh 核对不超卖。
# 用法：bench/chain-throughput.sh <activityId> [stock=1000]
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
AID=${1:?activityId 必填}; STOCK=${2:-1000}
source "$HERE/drills/common.sh"
bash "$HERE/prepare.sh" "$AID" "$STOCK" | tail -1
api POST /admin/seckill/consumer/pause >/dev/null
trap 'api POST /admin/seckill/consumer/resume >/dev/null' EXIT
bash "$HERE/run.sh" seckill-e2e.js AID="$AID" USERS=5000 VUS=1000 POLL=0 | grep -E "orders_queued|sold_out|unexpected" || true
Q=$(rc GET "seckill:inflight:$AID")
echo "积压待落单：${Q}（主题积压 $(api GET /admin/seckill/health | jq_ 'd["data"]["lag"]')）"
T0=$(python3 -c 'import time;print(time.time())')
api POST /admin/seckill/consumer/resume >/dev/null
while [ "$(rc GET "seckill:inflight:$AID")" != "0" ]; do sleep 0.2; done
T1=$(python3 -c 'import time;print(time.time())')
python3 -c "q=$Q;d=$T1-$T0;print(f'落单 {q} 单，耗时 {d:.1f}s → 纯链路吞吐 {q/d:.0f} 单/s')"
bash "$HERE/verify.sh" "$AID"
