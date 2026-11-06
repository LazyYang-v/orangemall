#!/usr/bin/env bash
# 橙选商城恢复。
#   scripts/restore.sh <备份目录> --drill            恢复演练（默认）：一次性 MySQL/Redis 容器（tmpfs，不建卷）里恢复，
#                                                  逐表行数与 manifest 比对、Redis key 数比对、校验和比对；结束即销毁
#   scripts/restore.sh <备份目录> --into-live --yes  覆盖恢复到 compose 栈的 MySQL（破坏性：先自动再备份一次当前状态）
# Redis 不做覆盖恢复：线上 Redis 是缓存 + 秒杀热数据，以 DB 为准重建（后台「预热」/ 秒杀 repair），RDB 只做演练校验。
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC=${1:?用法：scripts/restore.sh <备份目录> [--drill | --into-live --yes]}
MODE=${2:---drill}
SRC="$(cd "$SRC" && pwd)"
MYSQL_IMAGE=${MYSQL_IMAGE:-mysql:8.0}
REDIS_IMAGE=${REDIS_IMAGE:-redis:7.0-alpine}

(cd "$SRC" && shasum -a 256 -c SHA256SUMS >/dev/null) || { echo "✗ 校验和不匹配，备份文件损坏"; exit 1; }
echo "✓ 校验和一致（${SRC}）"

verify_tables() { # verify_tables <mysql 容器>：逐表比对行数
  local c=$1 bad=0 n=0
  while IFS=$'\t' read -r kind name want; do
    [ "$kind" = table ] || continue
    n=$((n + 1))
    got=$(docker exec -e MYSQL_PWD=root "$c" mysql -uroot -N -e "SELECT COUNT(*) FROM \`${name%%.*}\`.\`${name#*.}\`" 2>/dev/null || echo MISSING)
    [ "$got" = "$want" ] || { echo "  ✗ ${name}：备份 $want / 恢复 $got"; bad=$((bad + 1)); }
  done <"$SRC/manifest.tsv"
  [ $bad = 0 ] && echo "✓ MySQL：$n 张表行数与备份清单逐表一致" || { echo "✗ MySQL：$bad / $n 张表不一致"; return 1; }
}

case "$MODE" in
--drill)
  TAG="om-restore-$$"
  trap 'docker rm -f "$TAG-mysql" "$TAG-redis" >/dev/null 2>&1 || true' EXIT
  docker run -d --name "$TAG-mysql" --tmpfs /var/lib/mysql -e MYSQL_ROOT_PASSWORD=root \
    -v "$ROOT/backend/deploy/mysql/conf/my.cnf:/etc/mysql/conf.d/mall.cnf:ro" "$MYSQL_IMAGE" >/dev/null
  for _ in $(seq 1 90); do docker exec -e MYSQL_PWD=root "$TAG-mysql" mysql -uroot -e "SELECT 1" >/dev/null 2>&1 && break; sleep 2; done
  T0=$(date +%s)
  gunzip -c "$SRC/mysql.sql.gz" | docker exec -i -e MYSQL_PWD=root "$TAG-mysql" mysql -uroot --default-character-set=utf8mb4
  echo "  MySQL 恢复耗时 $(( $(date +%s) - T0 ))s"
  verify_tables "$TAG-mysql"

  docker run -d --name "$TAG-redis" --tmpfs /data "$REDIS_IMAGE" sleep 3600 >/dev/null
  # docker cp 写不进 tmpfs 挂载点，经 stdin 写入
  docker exec -i "$TAG-redis" sh -c 'cat > /data/dump.rdb' <"$SRC/redis.rdb"
  docker exec -d "$TAG-redis" redis-server --dir /data --dbfilename dump.rdb --appendonly no --port 6379
  for _ in $(seq 1 30); do [ "$(docker exec "$TAG-redis" redis-cli ping 2>/dev/null)" = PONG ] && break; sleep 1; done
  want=$(awk -F'\t' '/^redis_keys/{print $2}' "$SRC/manifest.tsv")
  got=$(docker exec "$TAG-redis" redis-cli DBSIZE)
  # 带 TTL 的 key（结果缓存/验证码等）在备份→演练间隔内可能过期，允许减少，不允许凭空增加
  if [ "$got" = "$want" ]; then echo "✓ Redis：RDB 载入成功，key 数 $got 与备份一致"
  elif [ "$got" -lt "$want" ] && [ "$got" -gt 0 ]; then echo "✓ Redis：RDB 载入成功，key $got / 备份 ${want}（差额为已过期的 TTL key）"
  else echo "✗ Redis：key 数 ${got}，备份 $want"; exit 1; fi
  echo "✓ 恢复演练通过（临时容器已销毁，未触碰任何线上容器与数据卷）"
  ;;
--into-live)
  [ "${3:-}" = "--yes" ] || { echo "覆盖线上 MySQL 需显式确认：scripts/restore.sh $SRC --into-live --yes"; exit 1; }
  echo "先备份当前状态（便于反悔）…"; "$ROOT/scripts/backup.sh"
  COMPOSE=(docker compose -f "$ROOT/backend/deploy/docker-compose.yml")
  C=$("${COMPOSE[@]}" ps -q mysql)
  gunzip -c "$SRC/mysql.sql.gz" | docker exec -i -e MYSQL_PWD=root "$C" mysql -uroot --default-character-set=utf8mb4
  verify_tables "$C"
  echo "已恢复到线上 MySQL。建议：重启业务服务，并在后台对进行中的秒杀活动执行「预热」以按 DB 重建 Redis 库存"
  ;;
*) echo "未知模式 ${MODE}（--drill | --into-live --yes）"; exit 1 ;;
esac
