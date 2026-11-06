#!/usr/bin/env bash
# 热点 SKU 普通下单压测：N 个压测用户在同一 SKU 上持续 preview → submit（order-submit 全局事务），
# 统计成功单数 / 吞吐 / 提交耗时分位 / 失败码分布，并核对「库存减少 = 订单数」；结束时取消压测订单，库存回到原值。
# Seata AT 下同一 SKU 行的全局锁从「扣库存分支注册」一直持有到全局提交，热点商品的并发下单在这把锁上排队。
# 用法：bench/hot-sku.sh [skuId]   环境变量 N（默认 20）、DURATION（默认 20 秒）、TAG（结果文件名后缀）
source "$(dirname "$0")/drills/common.sh"
SKU=${1:-$(sql "SELECT s.id FROM mall_product.sku s JOIN mall_product.spu p ON p.id=s.spu_id WHERE p.status='ON_SALE' ORDER BY s.stock DESC LIMIT 1")}
N=${N:-20}; DURATION=${DURATION:-20}; TAG=${TAG:-run}
WORK=$(mktemp -d); trap 'rm -rf "$WORK"' EXIT
stock() { sql "SELECT stock FROM mall_product.sku WHERE id=$SKU"; }
ledger() { sql "SELECT COUNT(*) FROM mall_product.stock_ledger WHERE sku_id=$SKU AND op='$1' AND created_at >= '$T0'"; }

S0=$(stock); T0=$(sql "SELECT NOW(3)")
echo "热点 SKU ${SKU}，库存 ${S0}，${N} 用户 × ${DURATION}s"
for i in $(seq 0 $((N - 1))); do submit_loop "$i" "$SKU" "$DURATION" "$WORK/r$i" & done
wait
sleep 2
IDS=$(bench_orders_since "$N" "$T0"); NO=$(echo "$IDS" | grep -c . || true); S1=$(stock)

REPORT=$(cat "$WORK"/r* | python3 -c '
import sys
rows=[l.split() for l in sys.stdin if l.strip()]
sub=[(r[0],int(r[1])) for r in rows if r[0].startswith("submit:")]
ok=sorted(ms for c,ms in sub if c=="submit:0")
def pct(a,p): return a[min(len(a)-1,int(len(a)*p))] if a else 0
from collections import Counter
cnt=Counter(r[0] for r in rows)
dur='"$DURATION"'
print("提交 %d 次，成功 %d（%.1f%%），成功吞吐 %.1f 单/s；成功提交耗时 p50 %dms / p95 %dms / max %dms" % (
  len(sub), len(ok), 100.0*len(ok)/max(1,len(sub)), len(ok)/dur, pct(ok,.5), pct(ok,.95), ok[-1] if ok else 0))
print("结果码：" + "  ".join("%s×%d" % (k,v) for k,v in sorted(cnt.items(), key=lambda x:-x[1])))
')
echo "$REPORT" | sed 's/^/  /'
echo "  库存 $S0 → ${S1}（-$((S0 - S1))），新订单 ${NO}"
ok=1
[ $((S0 - S1)) = "$NO" ] || { echo "  ✗ 库存减少 $((S0 - S1)) ≠ 订单数 ${NO}"; ok=0; }
D=$(ledger DEDUCT); [ "$D" = "$NO" ] || { echo "  ✗ DEDUCT 流水 ${D} ≠ 订单数 ${NO}"; ok=0; }

mkdir -p "$BENCH/results/raw"
{ echo "# hot-sku $TAG $(date '+%F %T') sku=$SKU N=$N DURATION=$DURATION"; echo "$REPORT"; echo "库存 -$((S0 - S1)) = 订单 $NO"; } \
  > "$BENCH/results/raw/hot-sku-$TAG.txt"

cancel_orders $IDS
sleep 2; S2=$(stock); R=$(ledger RESTORE); echo "  取消压测订单后库存 ${S2}（压测前 ${S0}），流水 DEDUCT ${D} / RESTORE ${R}"
[ "$R" = "$NO" ] || { echo "  ✗ RESTORE 流水 ${R} ≠ 订单数 ${NO}"; ok=0; }
[ "$S2" = "$S0" ] || { echo "  ✗ 取消后库存未回到压测前"; ok=0; }
[ $ok = 1 ] && echo "  ✓ 库存、流水与订单一致，取消后库存复原" || exit 1
