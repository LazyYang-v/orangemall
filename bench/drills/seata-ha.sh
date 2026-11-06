#!/usr/bin/env bash
# Phase 5 演练 F「TC 高可用：两个 TC 之一宕机」：前提是以 HA 方式部署
#   （.env 或 shell 里 SEATA_ADDR=seata:8091;seata-2:8091，docker compose --profile app --profile ha up -d）。
# 20 个压测用户持续普通下单（order-submit 全局事务），第 KILL_AT 秒 docker kill 掉一个 TC（默认 seata，模拟进程崩溃），
# DOWN 秒后再拉起。验证：
# 同时（CANCEL_DURING=1，默认开）后台每秒取消刚下的压测订单：关单回补与在途 / 孤儿下单事务争同一 SKU 行，
# 覆盖「非全局事务写 + AT 回滚」的脏写场景（Phase 5 演练中实际触发过 dirty undo → TimeoutRollbackFailed）。验证：
#   ① kill 后 RECOVER_S 秒（默认 20）起不再有失败提交；全程失败只能是可重试的 503，不出现 500；报告总成功率与逐 2 秒分布
#   ② 被杀 TC 上的在途全局事务由另一个 TC 接手收敛（共用 seata 库）：TC 表清空、无回滚失败的会话、undo_log 回到基线
#   ③ 数据一致：库存减少 = 订单数 − RESTORE 流水，DEDUCT 流水 = 订单数；关单回补后台重试队列清空，无「关单补偿失败」告警
# 结束时取消全部演练订单，库存回到原值。用法：bench/drills/seata-ha.sh [skuId]
#   环境变量 TC（seata|seata-2）、KILL_AT、DOWN、DURATION、RECOVER_S、CANCEL_DURING、TAG（原始数据 results/raw/seata-ha-$TAG.txt）
source "$(dirname "$0")/common.sh"
SKU=${1:-$(sql "SELECT s.id FROM mall_product.sku s JOIN mall_product.spu p ON p.id=s.spu_id WHERE p.status='ON_SALE' ORDER BY s.stock DESC LIMIT 1")}
N=${N:-20}; DURATION=${DURATION:-30}; KILL_AT=${KILL_AT:-8}; DOWN=${DOWN:-12}; TC=${TC:-seata}; RECOVER_S=${RECOVER_S:-20}; CANCEL_DURING=${CANCEL_DURING:-1}
WORK=$(mktemp -d); trap 'rm -rf "$WORK"' EXIT
stock() { sql "SELECT stock FROM mall_product.sku WHERE id=$SKU"; }
ledger() { sql "SELECT COUNT(*) FROM mall_product.stock_ledger WHERE sku_id=$SKU AND op='$1' AND created_at >= '$T0'"; }
pending_since() {
  sql "SELECT id FROM (SELECT id,user_id,created_at,source,status FROM mall_order.t_order_0 UNION ALL SELECT id,user_id,created_at,source,status FROM mall_order.t_order_1
       UNION ALL SELECT id,user_id,created_at,source,status FROM mall_order.t_order_2 UNION ALL SELECT id,user_id,created_at,source,status FROM mall_order.t_order_3) o
       WHERE user_id BETWEEN $BENCH_LO AND $((BENCH_LO + N - 1)) AND source='NORMAL' AND status='PENDING_PAYMENT' AND created_at >= '$1'"
}
health() { docker inspect -f '{{.State.Health.Status}}' "orange-mall-$1-1" 2>/dev/null || echo absent; }

ADDR=$(docker exec orange-mall-mall-order-1 printenv SEATA_ADDR)
case "$ADDR" in *seata-2*) ;; *) echo "业务服务 SEATA_ADDR=${ADDR}，未以 HA 方式部署（见脚本头注释）"; exit 1 ;; esac
for t in seata seata-2; do [ "$(health $t)" = healthy ] || { echo "TC $t 状态 $(health $t)，需两个 TC 都 healthy"; exit 1; }; done
[ "$(seata_rows)" = 0 ] || { echo "TC 表非空（$(seata_rows) 行），先等待在途事务结束再演练"; exit 1; }

S0=$(stock); U0=$(undo_rows); T0=$(sql "SELECT NOW(3)"); L0=$(date +%s)
[ "$(restore_backlog)" = 0 ] || { echo "关单回补后台重试队列非空（$(restore_backlog) 单），先等它清空"; exit 1; }
echo "SKU $SKU 库存 ${S0}，undo_log ${U0}，SEATA_ADDR=${ADDR}；${N} 用户 × ${DURATION}s，第 ${KILL_AT}s kill ${TC}，${DOWN}s 后拉起"
for i in $(seq 0 $((N - 1))); do submit_loop "$i" "$SKU" "$DURATION" "$WORK/r$i" & done
if [ "$CANCEL_DURING" = 1 ]; then
  touch "$WORK/running"
  ( while [ -f "$WORK/running" ]; do cancel_orders $(pending_since "$T0"); sleep 1; done ) &
  CANCELLER=$!
fi
sleep "$KILL_AT"
echo "1) kill ${TC}（在途全局事务 $(sql 'SELECT COUNT(*) FROM seata.global_table')）"
KMS=$(python3 -c 'import time;print(int(time.time()*1000))')
docker kill "orange-mall-${TC}-1" >/dev/null
sleep "$DOWN"
docker start "orange-mall-${TC}-1" >/dev/null
for _ in $(seq 1 40); do [ "$(health "$TC")" = healthy ] && break; sleep 2; done
echo "   ${TC} 已恢复（$(health "$TC")）"
for p in $(jobs -p); do [ "$p" = "${CANCELLER:-}" ] || wait "$p"; done
rm -f "$WORK/running"; [ -n "${CANCELLER:-}" ] && wait "$CANCELLER"

REPORT=$(cat "$WORK"/r* | python3 -c '
import sys
from collections import Counter
rows=[l.split() for l in sys.stdin if l.strip()]
c=Counter(r[0] for r in rows); sub=[r for r in rows if r[0].startswith("submit:")]
ok=sum(1 for r in sub if r[0]=="submit:0")
print("%d %d %.1f" % (len(sub), ok, 100.0*ok/max(1,len(sub))))
print("结果码：" + "  ".join("%s×%d" % kv for kv in c.most_common()))')
read -r NSUB NOK PCT <<<"$(echo "$REPORT" | head -1)"
echo "   提交 ${NSUB} 次，成功 ${NOK}（${PCT}%）；$(echo "$REPORT" | tail -1)"
E500=$(cut -d' ' -f1 "$WORK"/r* | grep -c '^submit:500$' || true)
# 按「相对 kill 时刻」每 2 秒一档的成功 / 失败分布，原始数据留档 results/raw/seata-ha-$TAG.txt
RAW="$BENCH/results/raw/seata-ha-${TAG:-run}.txt"; mkdir -p "$(dirname "$RAW")"
cat "$WORK"/r* | sort -k3 -n > "$RAW"
echo "   相对 kill 时刻（s）：成功/失败"
python3 - "$RAW" "$KMS" <<'PY' | sed 's/^/     /'
import sys
from collections import defaultdict
raw,k=sys.argv[1],int(sys.argv[2]); b=defaultdict(lambda:[0,0])
for l in open(raw):
    f=l.split()
    if len(f)<3 or not f[0].startswith("submit:"): continue
    s=(int(f[2])-k)//2000*2; b[s][0 if f[0]=="submit:0" else 1]+=1
print("  ".join("%+d:%d/%d" % (s,v[0],v[1]) for s,v in sorted(b.items())))
PY
LATE=$(awk -v k="$KMS" -v r="$RECOVER_S" '$1 ~ /^submit:/ && $1 != "submit:0" && $3 >= k + r * 1000' "$RAW" | wc -l | tr -d ' ')

echo "2) 等待 TC 表与 undo_log 收敛、关单回补后台重试清空"
for _ in $(seq 1 60); do [ "$(seata_rows)" = 0 ] && [ "$(undo_rows)" -le "$U0" ] && break; sleep 2; done
wait_restore_drain 150; BACKLOG=$(restore_backlog)
DEFER=$(order_log_count '关单补偿延后' "$L0"); REDONE=$(order_log_count '关单补偿完成' "$L0")
echo "   关单回补转后台重试 ${DEFER} 单，后台补成 ${REDONE} 单，队列剩余 ${BACKLOG}"
R1=$(seata_rows); U1=$(undo_rows); S1=$(stock); IDS=$(bench_orders_since "$N" "$T0"); NO=$(echo "$IDS" | grep -c . || true)
D=$(ledger DEDUCT); RS=$(ledger RESTORE)
# 12 RollbackFailed / 14 TimeoutRollbackFailed：终态，TC 不再重试，其全局锁永久占用（需人工按 undo 校准）
RF=$(sql "SELECT COUNT(*) FROM seata.global_table WHERE status IN (12, 14)")
echo "   TC 表剩余 ${R1} 行（回滚失败 ${RF}），待处理 undo_log ${U1}（基线 ${U0}），库存 $S0 → ${S1}，新订单 ${NO}，流水 DEDUCT ${D} / RESTORE ${RS}"

ok=1
[ "$LATE" = 0 ] || { echo "  ✗ kill 后 ${RECOVER_S}s 起仍有 ${LATE} 次失败提交（未在预期时间内恢复）"; ok=0; }
[ "$RF" = 0 ] || { echo "  ✗ ${RF} 个全局事务回滚失败（dirty undo？）"; ok=0; }
[ "$E500" = 0 ] || { echo "  ✗ 出现 ${E500} 次 500（应为可重试的 503）"; ok=0; }
[ "$R1" = 0 ] || { echo "  ✗ TC 表未清空（悬挂事务 ${R1} 行）"; ok=0; }
[ "$U1" -le "$U0" ] || { echo "  ✗ undo_log 未回到基线（${U1} > ${U0}）"; ok=0; }
[ $((S0 - S1)) = $((NO - RS)) ] || { echo "  ✗ 库存减少 $((S0 - S1)) ≠ 订单数 ${NO} − 回补 ${RS}"; ok=0; }
[ "$D" = "$NO" ] || { echo "  ✗ DEDUCT 流水 ${D} ≠ 订单数 ${NO}"; ok=0; }
[ "$BACKLOG" = 0 ] || { echo "  ✗ 关单回补后台重试 150s 内未清空（${BACKLOG} 单）"; ok=0; }

echo "3) 取消演练订单"
cancel_orders $IDS
sleep 3; wait_restore_drain 150 || true; echo "   库存 $(stock)（演练前 ${S0}）"
[ "$(stock)" = "$S0" ] || { echo "  ✗ 取消后库存未回到演练前"; ok=0; }
ALERTS=$(order_log_count '关单补偿失败' "$L0")
[ "$ALERTS" = 0 ] || { echo "  ✗ ${ALERTS} 条关单补偿失败告警（需人工按 stock_ledger 校准）"; ok=0; }
[ $ok = 1 ] && echo "  ✓ TC 高可用：kill ${TC} ${DOWN}s，总成功率 ${PCT}%、+${RECOVER_S}s 起零失败、无 500；在途事务由另一 TC 收敛，库存 / 流水 / 订单一致" || exit 1
