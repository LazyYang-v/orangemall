#!/usr/bin/env bash
# 为一次真实链路压测准备活动：设定库存 → 清理上一轮压测用户的成交/结果/path 计数 → 后台预热。
# 用法：bench/prepare.sh <activityId> [stock=1000]
# 只动压测用户（id 1770000000000000000 ~ +4999）与指定活动，不影响真实用户数据。
set -euo pipefail
cd "$(dirname "$0")/../backend/deploy"
AID=${1:?activityId 必填（后台 秒杀活动 页面或 /api/seckill/sessions 查看进行中场次）}
STOCK=${2:-1000}
GW=${GW:-http://localhost:18080/api}
LO=1770000000000000000; HI=1770000000000004999

sql() { docker compose exec -T mysql mysql -uroot -proot --default-character-set=utf8mb4 -N -e "$1" 2>/dev/null; }
rc() { docker compose exec -T redis redis-cli "$@"; }

[ "$(sql "SELECT COUNT(*) FROM mall_user.user WHERE id BETWEEN $LO AND $HI")" = "5000" ] || {
  echo "压测用户未就绪，先执行：docker compose exec -T mysql mysql -uroot -proot --default-character-set=utf8mb4 < bench/sql/seed-bench-users.sql"; exit 1; }

ADM=$(curl -s -XPOST "$GW/auth/login" -H 'Content-Type: application/json' \
  -d '{"phone":"18800000000","password":"admin123"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["token"])')

# 静默态：上一轮（含中途失败的轮次）积压的消息必须先消费完，否则清理与重置库存期间仍有消息落单，
# 本轮核对会出现「成交 > 总库存」的假超卖。确保消费者运行，等主题积压与本活动在途都归零。
curl -s -XPOST "$GW/admin/seckill/consumer/resume" -H "Authorization: Bearer $ADM" >/dev/null
for t in $(seq 1 300); do
  LAG=$(curl -s "$GW/admin/seckill/health" -H "Authorization: Bearer $ADM" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["lag"])')
  INF=$(rc GET "seckill:inflight:$AID"); INF=${INF:-0}
  [ "$LAG" = "0" ] && [ "$INF" = "0" ] && break
  [ "$t" = "300" ] && { echo "等待静默超时：主题积压 ${LAG}，在途 ${INF}"; exit 1; }
  sleep 1
done

sql "UPDATE mall_seckill.seckill_activity SET total_stock = $STOCK WHERE id = '$AID'"
sql "DELETE FROM mall_seckill.seckill_order WHERE activity_id = '$AID' AND user_id BETWEEN $LO AND $HI"
# 结果 key / path 频控计数：避免上一轮的 SUCCESS 或 5 次/分钟计数影响本轮
for pat in "seckill:result:$AID:177*" "seckill:pathcnt:$AID:177*" "seckill:path:$AID:177*"; do
  rc --scan --pattern "$pat" | xargs -r -n 500 docker compose exec -T redis redis-cli DEL >/dev/null || true
done
for i in $(seq 0 4999); do echo "DEL seckill:bucket:$((LO + i))"; done | rc >/dev/null

curl -s -XPOST "$GW/admin/seckill/$AID/warmup" -H "Authorization: Bearer $ADM" >/dev/null
echo "已准备 ${AID}：Redis 库存 $(rc GET seckill:stock:$AID)，DB 库存 $(sql "SELECT stock FROM mall_seckill.seckill_activity WHERE id='$AID'")，在途 $(rc GET seckill:inflight:$AID)"
date +%s > /tmp/om-bench-start
