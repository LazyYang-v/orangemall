#!/usr/bin/env bash
# OM-013 演练 A「消费者宕机」：暂停主消费者 → 真实用户抢购（在途 > 0 且主题积压）→ 滞留告警（只告警不重置）
# → 恢复消费者 → 积压消化、在途归零 → 恢复告警。全程 Redis/DB 一致、不超卖。
# 用法：bench/drills/consumer-down.sh [activityId]   耗时约 stuckSeconds + 2 分钟（默认 180s → ~5 分钟）
source "$(dirname "$0")/common.sh"
AID=${1:-$(ongoing_aid)}
N=${N:-3}
T0=$(now_iso)
echo "活动 $AID  库存 Redis=$(rc GET seckill:stock:$AID) DB=$(sql "SELECT stock FROM mall_seckill.seckill_activity WHERE id='$AID'")"

echo "1) 暂停主消费者"; api POST /admin/seckill/consumer/pause >/dev/null
echo "2) $N 个压测用户抢购（17700004990 起，需已执行 bench/sql/seed-bench-users.sql）"
for i in $(seq 0 $((N - 1))); do
  ph=$(printf '177%08d' $((4990 + i)))
  tk=$(login "$ph" 123456)
  rc DEL "seckill:pathcnt:$AID:$((1770000000000004990 + i))" >/dev/null
  path=$(curl -s "$GW/seckill/$AID/path" -H "Authorization: Bearer $tk" | jq_ 'd["data"]["path"]')
  r=$(curl -s -XPOST "$GW/seckill/$AID/$path/orders" -H "Authorization: Bearer $tk" -H 'Content-Type: application/json' -d '{}' | jq_ 'd["code"]')
  echo "   $ph → code ${r}（0=排队中，7004=之前抢过）"
done
echo "   在途=$(rc GET seckill:inflight:$AID)  积压=$(api GET /admin/seckill/health | jq_ 'd["data"]["lag"]')"

echo "3) 等待滞留告警（在途不变 ≥ stuckSeconds，巡检每 60s 一轮）"
wait_alert "在途滞留] $AID" "$T0" 420
echo "   确认未被重置：在途=$(rc GET seckill:inflight:$AID)"

T1=$(now_iso)
echo "4) 恢复主消费者"; api POST /admin/seckill/consumer/resume >/dev/null
echo "5) 等待积压消化与恢复告警"
wait_alert "在途恢复] $AID" "$T1" 180
echo "   在途=$(rc GET seckill:inflight:$AID)  Redis=$(rc GET seckill:stock:$AID)  DB=$(sql "SELECT stock FROM mall_seckill.seckill_activity WHERE id='$AID'")"
