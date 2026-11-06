#!/usr/bin/env bash
# OM-019 演练 D「建单服务宕机」：停 mall-order → 压测用户抢购（DB 扣减 + seckill_order 已提交，Feign 建单失败）
# → 消费者重试 1/2/4/8/8s 耗尽 → 补偿（DB/Redis 库存回补、删除成交记录、移出已购集合、结果 FAILED）
# → 死信 EXHAUSTED 留档。验证合并扣减（SeckillDeductBatcher）与补偿路径的一致性。
# 用法：bench/drills/order-down.sh [activityId]   耗时约 1.5 分钟；结束时总会重新拉起 mall-order
source "$(dirname "$0")/common.sh"
AID=${1:-$(ongoing_aid)}
N=${N:-3}
BASE_UID=4980
stock() { echo "Redis=$(rc GET "seckill:stock:$AID") DB=$(sql "SELECT stock FROM mall_seckill.seckill_activity WHERE id='$AID'")"; }
dl_count() { sql "SELECT COUNT(*) FROM mall_seckill.seckill_dead_letter WHERE reason='EXHAUSTED' AND activity_id='$AID'"; }

trap 'docker compose start mall-order >/dev/null 2>&1 || true' EXIT
S0=$(stock); D0=$(dl_count)
echo "活动 $AID  库存 ${S0}  EXHAUSTED 死信 ${D0}"
# 已抢过的演练用户先清掉（可重复执行）
for i in $(seq 0 $((N - 1))); do
  u=$((1770000000000000000 + BASE_UID + i))
  sql "DELETE FROM mall_seckill.seckill_order WHERE activity_id='$AID' AND user_id=$u" >/dev/null
  rc SREM "seckill:bought:$AID" "$u" >/dev/null; rc DEL "seckill:result:$AID:$u" "seckill:pathcnt:$AID:$u" >/dev/null
done

# 先为各用户拿到 token 与 path（登录不依赖 mall-order），再停服务，避免把登录失败算进来
TOKENS=(); PATHS=()
for i in $(seq 0 $((N - 1))); do
  tk=$(login "$(printf '177%08d' $((BASE_UID + i)))" 123456)
  TOKENS+=("$tk")
  PATHS+=("$(curl -s "$GW/seckill/$AID/path" -H "Authorization: Bearer $tk" | jq_ 'd["data"]["path"]')")
done

echo "1) 停止 mall-order"; docker compose stop mall-order >/dev/null 2>&1
echo "2) ${N} 个压测用户抢购"
for i in $(seq 0 $((N - 1))); do
  r=$(curl -s -XPOST "$GW/seckill/$AID/${PATHS[$i]}/orders" -H "Authorization: Bearer ${TOKENS[$i]}" -H 'Content-Type: application/json' -d '{}' | jq_ 'd["code"]')
  echo "   用户 $((BASE_UID + i)) → code ${r}（0=排队中）"
done
sleep 3
echo "   扣减后：$(stock)  seckill_order(演练用户)=$(sql "SELECT COUNT(*) FROM mall_seckill.seckill_order WHERE activity_id='$AID' AND user_id BETWEEN $((1770000000000000000 + BASE_UID)) AND $((1770000000000000000 + BASE_UID + N - 1))")"

echo "3) 等待重试耗尽（约 23s）与补偿"
for _ in $(seq 1 60); do
  [ "$(rc GET "seckill:inflight:$AID")" = "0" ] && [ "$(dl_count)" -ge $((D0 + N)) ] && break
  sleep 2
done
S1=$(stock); D1=$(dl_count)
LEFT=$(sql "SELECT COUNT(*) FROM mall_seckill.seckill_order WHERE activity_id='$AID' AND user_id BETWEEN $((1770000000000000000 + BASE_UID)) AND $((1770000000000000000 + BASE_UID + N - 1))")
R0=$(rc GET "seckill:result:$AID:$((1770000000000000000 + BASE_UID))")
echo "   补偿后：${S1}  在途=$(rc GET "seckill:inflight:$AID")  seckill_order(演练用户)=${LEFT}  EXHAUSTED 死信 ${D0} → ${D1}"
echo "   用户结果：${R0}"

echo "4) 拉起 mall-order"; docker compose start mall-order >/dev/null 2>&1; trap - EXIT
ok=1
[ "$S1" = "$S0" ] || { echo "  ✗ 库存未完全回补（前 ${S0} / 后 ${S1}）"; ok=0; }
[ "$LEFT" = "0" ] || { echo "  ✗ 成交记录未删除：${LEFT}"; ok=0; }
[ "$D1" -ge $((D0 + N)) ] || { echo "  ✗ EXHAUSTED 死信未达 +${N}"; ok=0; }
echo "$R0" | grep -q FAILED || { echo "  ✗ 用户结果不是 FAILED"; ok=0; }
[ $ok = 1 ] && echo "  ✓ 重试耗尽 → 补偿一致（库存回到 ${S0}、成交记录已删、死信 +${N}、结果 FAILED）" || exit 1
