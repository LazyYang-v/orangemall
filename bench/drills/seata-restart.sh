#!/usr/bin/env bash
# OM-017 演练 E「Seata TC 重启」：20 个压测用户持续普通下单（order-submit 全局事务：扣库存 → 建单），
# 压测中途重启 TC（store.mode=db）。验证：
#   ① TC 重启后全局事务表 / 分支表 / 全局锁表全部清空（在途事务被恢复并完成二阶段，不悬挂）
#   ② 数据一致：SKU 库存减少量 = 演练期间成功创建订单的购买件数（无「扣了库存没订单」或反之）
#   ③ 待处理 undo_log 回到基线（二阶段提交异步删除回滚日志；防悬挂标记 log_status=1 不计）
#   ④ TC 恢复后新下单立即成功；TC 不可用窗口内的失败返回可重试的 503，不出现 500
# 结束时取消演练产生的订单（库存经正常取消流程回补）。用法：bench/drills/seata-restart.sh [skuId]   耗时约 1.5 分钟
source "$(dirname "$0")/common.sh"
SKU=${1:-$(sql "SELECT s.id FROM mall_product.sku s JOIN mall_product.spu p ON p.id=s.spu_id WHERE p.status='ON_SALE' ORDER BY s.stock DESC LIMIT 1")}
N=${N:-20}; DURATION=${DURATION:-24}; RESTART_AT=${RESTART_AT:-6}
WORK=$(mktemp -d); trap 'rm -rf "$WORK"' EXIT

stock() { sql "SELECT stock FROM mall_product.sku WHERE id=$SKU"; }
orders_since() { bench_orders_since "$N" "$1"; }

[ "$(seata_rows)" = 0 ] || { echo "TC 表非空（$(seata_rows) 行），先等待在途事务结束再演练"; exit 1; }
S0=$(stock); U0=$(undo_rows); T0=$(sql "SELECT NOW(3)")
echo "SKU $SKU 库存 ${S0}，undo_log ${U0}，开始时间 $T0"

for i in $(seq 0 $((N - 1))); do submit_loop "$i" "$SKU" "$DURATION" "$WORK/r$i" & done
sleep "$RESTART_AT"
echo "1) 第 ${RESTART_AT}s：重启 TC（在途全局事务 $(sql 'SELECT COUNT(*) FROM seata.global_table')）"
docker compose restart seata >/dev/null 2>&1
for _ in $(seq 1 40); do [ "$(docker inspect -f '{{.State.Health.Status}}' orange-mall-seata-1)" = healthy ] && break; sleep 2; done
echo "   TC 已恢复（healthy）"
wait
cut -d' ' -f1 "$WORK"/r* | sort | uniq -c | sed 's/^/   /'
E500=$(cut -d' ' -f1 "$WORK"/r* | grep -c '^submit:500$' || true)

echo "2) 等待 TC 表与 undo_log 收敛"
for _ in $(seq 1 60); do [ "$(seata_rows)" = 0 ] && [ "$(undo_rows)" -le "$U0" ] && break; sleep 2; done
R1=$(seata_rows); U1=$(undo_rows); S1=$(stock); IDS=$(orders_since "$T0"); NO=$(echo "$IDS" | grep -c . || true)
echo "   TC 表剩余 ${R1} 行，待处理 undo_log ${U1}（基线 ${U0}；防悬挂标记 $(undo_q "log_status=1") 行），库存 $S0 → ${S1}，新订单 $NO"

echo "3) TC 恢复后再下一单"
tk=$(login 17700004900 123456)
body=$(curl -s -XPOST "$GW/orders/preview" -H "Authorization: Bearer $tk" -H 'Content-Type: application/json' -d "{\"items\":[{\"skuId\":$SKU,\"count\":1}]}" \
  | python3 -c 'import sys,json;p=json.load(sys.stdin)["data"];print(json.dumps({"submitToken":p["submitToken"],"addressId":p["address"]["id"],"items":[{"skuId":'"$SKU"',"count":1}]}))')
AFTER=$(curl -s -XPOST "$GW/orders" -H "Authorization: Bearer $tk" -H 'Content-Type: application/json' -d "$body" | jq_ 'd["code"]')
echo "   code ${AFTER}"

ok=1
# 用第 2 步收敛时的采样判定：第 3 步新下单的分支 / undo_log 是正常的瞬时行，不算悬挂
[ "$R1" = 0 ] || { echo "  ✗ TC 表未清空（悬挂事务 ${R1} 行）"; ok=0; }
[ "$U1" -le "$U0" ] || { echo "  ✗ undo_log 未回到基线（${U1} > ${U0}）"; ok=0; }
[ $((S0 - S1)) = "$NO" ] || { echo "  ✗ 库存减少 $((S0 - S1)) ≠ 订单数 ${NO}（扣减与建单不一致）"; ok=0; }
[ "$E500" = 0 ] || { echo "  ✗ TC 不可用期间出现 ${E500} 次 500（应映射为可重试的 503）"; ok=0; }
[ "$AFTER" = 0 ] || { echo "  ✗ TC 恢复后下单失败 code=${AFTER}"; ok=0; }

echo "4) 取消演练订单（库存经取消流程回补）"
cancel_orders $(orders_since "$T0")
sleep 3; echo "   库存 $(stock)（演练前 ${S0}）"
[ "$(stock)" = "$S0" ] || { echo "  ✗ 取消后库存未回到演练前"; ok=0; }
[ $ok = 1 ] && echo "  ✓ TC 重启：在途事务全部收敛、库存与订单一致（-$((S0 - S1)) 件 = $NO 单）、undo_log 回到基线、恢复后下单成功" || exit 1
