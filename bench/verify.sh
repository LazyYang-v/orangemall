#!/usr/bin/env bash
# 真实链路压测后的「不超卖」核对（DB / Redis 为准，不信任客户端统计）。
# 用法：bench/verify.sh <activityId>
# 判定：库存消耗 == 成交行数 == 订单数；Redis、DB 库存均 ≥ 0；Redis == DB − 在途；无用户 2 单；在途归零。
set -euo pipefail
cd "$(dirname "$0")/../backend/deploy"
AID=${1:?activityId 必填}
sql() { docker compose exec -T mysql mysql -uroot -proot --default-character-set=utf8mb4 -N -e "$1" 2>/dev/null; }
rc() { docker compose exec -T redis redis-cli "$@"; }

# 等在途归零（最长 120s）
for _ in $(seq 1 60); do [ "$(rc GET seckill:inflight:$AID)" = "0" ] && break; sleep 2; done

read -r TOTAL DB <<<"$(sql "SELECT total_stock, stock FROM mall_seckill.seckill_activity WHERE id='$AID'")"
REDIS=$(rc GET seckill:stock:$AID); INFLIGHT=$(rc GET seckill:inflight:$AID); BOUGHT=$(rc SCARD seckill:bought:$AID)
SO=$(sql "SELECT COUNT(*) FROM mall_seckill.seckill_order WHERE activity_id='$AID'")
DUP=$(sql "SELECT COUNT(*) FROM (SELECT user_id FROM mall_seckill.seckill_order WHERE activity_id='$AID' GROUP BY user_id HAVING COUNT(*)>1) t")
ORD=$(sql "SELECT COUNT(*) FROM (SELECT id FROM mall_order.t_order_0 UNION ALL SELECT id FROM mall_order.t_order_1 UNION ALL SELECT id FROM mall_order.t_order_2 UNION ALL SELECT id FROM mall_order.t_order_3) o JOIN mall_seckill.seckill_order s ON s.order_id = o.id WHERE s.activity_id='$AID'")
read -r T0 T1 SPAN <<<"$(sql "SELECT DATE_FORMAT(MIN(created_at),'%H:%i:%s.%f'), DATE_FORMAT(MAX(created_at),'%H:%i:%s.%f'), TIMESTAMPDIFF(MICROSECOND, MIN(created_at), MAX(created_at))/1e6 FROM mall_seckill.seckill_order WHERE activity_id='$AID'")"
DLT=$(sql "SELECT COUNT(*) FROM mall_seckill.seckill_dead_letter WHERE activity_id='$AID'")
SOLD=$((TOTAL - DB))

echo "活动            $AID"
echo "总库存 / DB 剩余 / Redis 剩余 / 在途   $TOTAL / $DB / $REDIS / $INFLIGHT"
echo "库存消耗(总-DB) $SOLD"
echo "seckill_order   ${SO}（重复用户 ${DUP}）"
echo "订单(mall_order) $ORD"
echo "Redis 已购集合  $BOUGHT"
echo "死信            $DLT"
echo "成交时间窗      $T0 → ${T1}（${SPAN}s）"
if [ "$SO" -gt 0 ]; then
  echo "落单吞吐        $(python3 -c "s=float('${SPAN:-0}');print(f'{$SO/s:.0f}' if s>0 else '—')") 单/s（成交行数 / 首末成交时间差）"
fi

ok=1
[ "$DB" -ge 0 ] && [ "$REDIS" -ge 0 ] || { echo "✗ 库存为负（超卖）"; ok=0; }
[ "$SOLD" -eq "$SO" ] || { echo "✗ 库存消耗 $SOLD ≠ 成交 $SO"; ok=0; }
[ "$SO" -eq "$ORD" ] || { echo "✗ 成交 $SO ≠ 订单 $ORD"; ok=0; }
[ "$SO" -le "$TOTAL" ] || { echo "✗ 成交超过总库存"; ok=0; }
[ "$DUP" -eq 0 ] || { echo "✗ 存在一人多单"; ok=0; }
[ "$INFLIGHT" -eq 0 ] || { echo "✗ 在途未归零"; ok=0; }
[ "$REDIS" -eq $((DB - INFLIGHT)) ] || { echo "✗ Redis ≠ DB − 在途"; ok=0; }
[ $ok -eq 1 ] && echo "✓ 无超卖：库存消耗 = 成交 = 订单，Redis/DB 一致，无一人多单" || exit 1
