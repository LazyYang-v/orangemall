# 演练公共函数（被 drills/*.sh source）
set -euo pipefail
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
