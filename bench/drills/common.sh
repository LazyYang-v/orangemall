# 演练公共函数（被 drills/*.sh source）
set -euo pipefail
BENCH="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"   # bench/ 绝对路径（下面会 cd 到 deploy，之后 $0 相对路径失效）
DEPLOY="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../backend/deploy" && pwd)"
GW=${GW:-http://localhost:18080/api}
cd "$DEPLOY"

rc() { docker compose exec -T redis redis-cli "$@"; }
sql() { docker compose exec -T mysql mysql -uroot -proot --default-character-set=utf8mb4 -N -e "$1" 2>/dev/null; }
jq_() { python3 -c "import sys,json;d=json.load(sys.stdin);print($1)"; }

login() { curl -s -XPOST "$GW/auth/login" -H 'Content-Type: application/json' -d "{\"phone\":\"$1\",\"password\":\"$2\"}" | jq_ 'd["data"]["token"]'; }
ADM=$(login 18800000000 admin123)
api() { local m=$1 p=$2; shift 2; curl -s -X"$m" "$GW$p" -H "Authorization: Bearer $ADM" -H 'Content-Type: application/json' "$@"; }

# 进行中场次里剩余库存最多的活动
ongoing_aid() { curl -s "$GW/seckill/sessions" | jq_ 'sorted([i for s in d["data"] if s["status"]=="ONGOING" for i in s["items"]], key=lambda i:-i["remainStock"])[0]["activityId"]'; }

# 等待 ALERT 日志出现包含关键字的新条目（$1 关键字，$2 起始 ISO 时间，$3 超时秒）
wait_alert() {
  local kw=$1 since=$2 timeout=${3:-300} t=0
  while [ $t -lt "$timeout" ]; do
    local hit
    hit=$(api GET "/admin/logs?type=ALERT" | python3 -c "
import sys,json
for x in json.load(sys.stdin)['data']:
    if x['time'] >= '$since' and '$kw' in x['text']: print(x['time'][11:19], x['text']); break")
    if [ -n "$hit" ]; then echo "  ✓ $hit"; return 0; fi
    sleep 5; t=$((t + 5))
  done
  echo "  ✗ ${timeout}s 内未出现告警「${kw}」"; return 1
}
now_iso() { date -u +%Y-%m-%dT%H:%M:%S; }

# ---------- 普通下单压测（seata-restart / hot-sku 共用）：压测用户 4900 起 N 个，均已有默认收货地址 ----------
BENCH_LO=$((1770000000000000000 + 4900))
# 普通下单循环：preview（submitToken + 默认地址）→ submit，每次提交记一行「结果码 耗时ms 结束时刻epoch_ms」到 $4
#   $1 用户序号 $2 skuId $3 持续秒数 $4 结果文件
submit_loop() {
  local i=$1 sku=$2 dur=$3 out=$4 tk end body t0 t1 code
  tk=$(login "$(printf '177%08d' $((4900 + i)))" 123456)
  end=$(( $(date +%s) + dur ))
  while [ "$(date +%s)" -lt $end ]; do
    body=$(curl -s -m 15 -XPOST "$GW/orders/preview" -H "Authorization: Bearer $tk" -H 'Content-Type: application/json' \
        -d "{\"items\":[{\"skuId\":$sku,\"count\":1}]}" | python3 -c '
import sys,json
d=json.load(sys.stdin)
if d.get("code")!=0: print("preview:%s" % d.get("code")); sys.exit()
p=d["data"]; print(json.dumps({"submitToken":p["submitToken"],"addressId":p["address"]["id"],"items":[{"skuId":'"$sku"',"count":1}]}))' )
    case "$body" in preview:*) echo "$body 0" >>"$out"; sleep 0.5; continue ;; esac
    t0=$(python3 -c 'import time;print(int(time.time()*1000))')
    code=$(curl -s -m 15 -XPOST "$GW/orders" -H "Authorization: Bearer $tk" -H 'Content-Type: application/json' -d "$body" \
      | python3 -c 'import sys,json
try: print("submit:%s" % json.load(sys.stdin).get("code"))
except Exception: print("submit:timeout")')
    t1=$(python3 -c 'import time;print(int(time.time()*1000))')
    echo "$code $((t1 - t0)) $t1" >>"$out"
  done
}
# 压测用户（$1 个）在 $2 之后创建的普通订单 id
bench_orders_since() {
  local hi=$((BENCH_LO + $1 - 1))
  sql "SELECT id FROM (SELECT id,user_id,created_at,source FROM mall_order.t_order_0 UNION ALL SELECT id,user_id,created_at,source FROM mall_order.t_order_1
       UNION ALL SELECT id,user_id,created_at,source FROM mall_order.t_order_2 UNION ALL SELECT id,user_id,created_at,source FROM mall_order.t_order_3) o
       WHERE user_id BETWEEN $BENCH_LO AND $hi AND source='NORMAL' AND created_at >= '$2'"
}
# 以下单用户身份取消订单（库存经正常关单流程回补）：$@ = 订单 id
cancel_orders() {
  local id u t
  for id in "$@"; do
    u=$(sql "SELECT user_id FROM (SELECT id,user_id FROM mall_order.t_order_0 UNION ALL SELECT id,user_id FROM mall_order.t_order_1 UNION ALL SELECT id,user_id FROM mall_order.t_order_2 UNION ALL SELECT id,user_id FROM mall_order.t_order_3) o WHERE id=$id")
    t=$(login "177${u: -8}" 123456)
    curl -s -XPOST "$GW/orders/$id/cancel" -H "Authorization: Bearer $t" >/dev/null
  done
}

# ---------- Seata 收敛检查（seata-restart / seata-ha 共用） ----------
# TC 三张表（全局事务 / 分支 / 全局锁）剩余行数：0 = 没有在途或悬挂事务
seata_rows() { sql "SELECT (SELECT COUNT(*) FROM seata.global_table)+(SELECT COUNT(*) FROM seata.branch_table)+(SELECT COUNT(*) FROM seata.lock_table)"; }
# 只统计待二阶段处理的回滚日志（log_status=0）；log_status=1 是 TC 回滚「本地从未提交成功的分支」时 RM 写下的
# 防悬挂标记（GlobalFinished，rollback_info 为空），用于阻止迟到的本地提交，按 undo 清理周期（7 天）删除，属预期
undo_q() { local w=$1; sql "SELECT (SELECT COUNT(*) FROM mall_product.undo_log WHERE $w)+(SELECT COUNT(*) FROM mall_order.undo_log WHERE $w)+(SELECT COUNT(*) FROM mall_user.undo_log WHERE $w)+(SELECT COUNT(*) FROM mall_marketing.undo_log WHERE $w)"; }
undo_rows() { undo_q "log_status=0"; }

# 关单回补后台重试（OrderService#restoreStock）：待重试订单数；等它清空（$1 最多等的秒数）
restore_backlog() { rc ZCARD order:restore:retry | tr -d '\r'; }
wait_restore_drain() { local t=0; while [ "$(restore_backlog)" != 0 ] && [ $t -lt "${1:-150}" ]; do sleep 2; t=$((t + 2)); done; [ "$(restore_backlog)" = 0 ]; }
# mall-order 容器日志里自 $2（unix 秒）起出现关键字 $1 的次数（ops 日志 Redis 列表只留 200 条，演练期间会被冲掉）
order_log_count() { docker logs --since "$2" orange-mall-mall-order-1 2>&1 | grep -c "$1" || true; }
