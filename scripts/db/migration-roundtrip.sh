#!/usr/bin/env bash
# Flyway 迁移往返校验（Phase 4 DoD「迁移 up/down 往返」）：
#   对每个服务库，在一次性 MySQL（tmpfs，不建卷）里
#   ① 逐版本 up：flyway migrate -target=v，记录快照 S_v（结构 + 各表行数）
#   ② 逐版本 down：执行 db/rollback/U<v>__*.sql，删掉对应 history 行，结果必须等于 S_{v-1}（S_0 = 空库）
#   ③ 再次 up 到最新，结果必须等于 S_N
#   并校验每个 V<n> 都有对应的 U<n>。任何一步不一致即失败退出。
# 用法：scripts/db/migration-roundtrip.sh [服务名…]   默认全部 6 个库；需要 docker
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
if [ $# -gt 0 ]; then SERVICES=("$@"); else SERVICES=(user product marketing order pay seckill); fi
FLYWAY_IMAGE=${FLYWAY_IMAGE:-flyway/flyway:10.10.0}   # 与 Spring Boot 3.3.0 托管的 flyway-core 版本一致
MYSQL_IMAGE=${MYSQL_IMAGE:-mysql:8.0}
TAG="om-mig-$$"; NET="$TAG"; DB="$TAG-mysql"
WORK=$(mktemp -d)

cleanup() { docker rm -f "$DB" >/dev/null 2>&1 || true; docker network rm "$NET" >/dev/null 2>&1 || true; rm -rf "$WORK"; }
trap cleanup EXIT

docker network create "$NET" >/dev/null
docker run -d --name "$DB" --network "$NET" --network-alias mysql --tmpfs /var/lib/mysql \
  -e MYSQL_ROOT_PASSWORD=root -v "$ROOT/backend/deploy/mysql/conf/my.cnf:/etc/mysql/conf.d/mall.cnf:ro" \
  "$MYSQL_IMAGE" >/dev/null
for _ in $(seq 1 90); do
  docker exec "$DB" mysql -uroot -proot -e "SELECT 1" >/dev/null 2>&1 && break; sleep 2
done

sql() { docker exec -i "$DB" mysql -uroot -proot --default-character-set=utf8mb4 -N "$@" 2>/dev/null; }
flyway() { # flyway <db> <migration-dir> <args…>
  local db=$1 dir=$2; shift 2
  docker run --rm --network "$NET" -v "$dir:/flyway/sql:ro" "$FLYWAY_IMAGE" \
    -url="jdbc:mysql://mysql:3306/$db?allowPublicKeyRetrieval=true&useSSL=false" -user=root -password=root \
    -locations=filesystem:/flyway/sql -connectRetries=10 "$@" >"$WORK/flyway.log" 2>&1 \
    || { cat "$WORK/flyway.log"; return 1; }
}
# 快照：去掉 AUTO_INCREMENT 计数后的结构 + 各业务表行数（不含 flyway_schema_history）
snapshot() {
  local db=$1
  docker exec "$DB" mysqldump -uroot -proot --default-character-set=utf8mb4 --no-data --skip-comments \
      --ignore-table="$db.flyway_schema_history" "$db" 2>/dev/null | sed -E 's/ AUTO_INCREMENT=[0-9]+//'
  for t in $(sql -e "SELECT table_name FROM information_schema.tables WHERE table_schema='$db' AND table_name<>'flyway_schema_history' ORDER BY 1"); do
    echo "rows $t $(sql -e "SELECT COUNT(*) FROM $db.\`$t\`")"
  done
}

fail=0
for svc in "${SERVICES[@]}"; do
  bad=0; db="mall_$svc"
  mig="$ROOT/backend/mall-$svc/src/main/resources/db/migration"
  rb="$ROOT/backend/mall-$svc/src/main/resources/db/rollback"
  versions=($(ls "$mig" | sed -nE 's/^V([0-9]+)__.*\.sql$/\1/p' | sort -n))
  for v in "${versions[@]}"; do
    ls "$rb"/U"${v}"__*.sql >/dev/null 2>&1 || { echo "✗ ${db}：V$v 缺少回滚脚本 db/rollback/U${v}__*.sql"; bad=1; }
  done
  [ $bad = 0 ] || { fail=1; continue; }
  sql -e "CREATE DATABASE $db DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci"

  snapshot "$db" >"$WORK/$db.S0"
  for v in "${versions[@]}"; do
    flyway "$db" "$mig" -target="$v" migrate
    snapshot "$db" >"$WORK/$db.S$v"
  done
  N=${versions[${#versions[@]}-1]}
  for ((i=${#versions[@]}-1; i>=0; i--)); do
    v=${versions[$i]}; prev=$([ "$i" -gt 0 ] && echo "${versions[$((i-1))]}" || echo 0)
    sql "$db" <"$(ls "$rb"/U"${v}"__*.sql)" >/dev/null
    sql -e "DELETE FROM $db.flyway_schema_history WHERE version='$v'"
    snapshot "$db" >"$WORK/$db.D$v"
    if ! diff -q "$WORK/$db.S$prev" "$WORK/$db.D$v" >/dev/null; then
      echo "✗ ${db}：执行 U$v 后与 V$prev 状态不一致"; diff "$WORK/$db.S$prev" "$WORK/$db.D$v" | head -20; bad=1; break
    fi
  done
  [ $bad = 0 ] || { fail=1; continue; }
  flyway "$db" "$mig" migrate
  flyway "$db" "$mig" validate
  snapshot "$db" >"$WORK/$db.R"
  if diff -q "$WORK/$db.S$N" "$WORK/$db.R" >/dev/null; then
    echo "✓ ${db}：V1→V$N up，U${N}→U1 down 逐版本复原，再 up 与首次一致（$(grep -c '^CREATE TABLE' "$WORK/$db.R") 表，$(awk '/^rows/{s+=$3} END{print s+0}' "$WORK/$db.R") 行）"
  else
    echo "✗ ${db}：再次 up 后与首次结果不一致"; diff "$WORK/$db.S$N" "$WORK/$db.R" | head -20; fail=1
  fi
done
exit $fail
