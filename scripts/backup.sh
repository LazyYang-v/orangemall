#!/usr/bin/env bash
# 橙选商城备份：MySQL 全部业务库（mall_* + seata，一致性快照）+ Redis RDB + 清单（各表行数、Redis key 数、sha256）。
# 用法：scripts/backup.sh            → backups/<YYYYmmdd-HHMMSS>/
#       BACKUP_DIR=/data/bak BACKUP_KEEP=14 scripts/backup.sh
# 只读操作：不停服务、不锁表（InnoDB --single-transaction），不碰任何 docker 卷。
# 恢复 / 演练：scripts/restore.sh <备份目录> [--drill | --into-live --yes]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
COMPOSE=(docker compose -f "$ROOT/backend/deploy/docker-compose.yml")
BACKUP_DIR=${BACKUP_DIR:-$ROOT/backups}
BACKUP_KEEP=${BACKUP_KEEP:-7}
MYSQL_PWD_=${MYSQL_ROOT_PASSWORD:-root}
OUT="$BACKUP_DIR/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT"

my() { "${COMPOSE[@]}" exec -T -e MYSQL_PWD="$MYSQL_PWD_" mysql "$@"; }
q() { my mysql -uroot --default-character-set=utf8mb4 -N -e "$1"; }

DBS=($(q "SELECT schema_name FROM information_schema.schemata WHERE schema_name LIKE 'mall\\_%' OR schema_name = 'seata' ORDER BY 1"))
[ ${#DBS[@]} -gt 0 ] || { echo "未找到 mall_* 库，MySQL 是否在运行？"; exit 1; }

# 清单：备份时刻各表行数（演练时与恢复结果逐表比对）
{
  echo "# created_at $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "# mysql $(q 'SELECT VERSION()')"
  for db in "${DBS[@]}"; do
    for t in $(q "SELECT table_name FROM information_schema.tables WHERE table_schema='$db' AND table_type='BASE TABLE' ORDER BY 1"); do
      printf 'table\t%s.%s\t%s\n' "$db" "$t" "$(q "SELECT COUNT(*) FROM \`$db\`.\`$t\`")"
    done
  done
} >"$OUT/manifest.tsv"

echo "MySQL：${DBS[*]}"
my mysqldump -uroot --default-character-set=utf8mb4 --single-transaction --quick --routines --triggers --events \
  --set-gtid-purged=OFF --hex-blob --databases "${DBS[@]}" | gzip -6 >"$OUT/mysql.sql.gz"

echo "Redis：RDB 快照"
"${COMPOSE[@]}" exec -T redis sh -c 'redis-cli --rdb /tmp/om-backup.rdb >/dev/null 2>&1 && cat /tmp/om-backup.rdb && rm -f /tmp/om-backup.rdb' >"$OUT/redis.rdb"
printf 'redis_keys\t%s\n' "$("${COMPOSE[@]}" exec -T redis redis-cli DBSIZE | tr -d '\r')" >>"$OUT/manifest.tsv"

(cd "$OUT" && shasum -a 256 mysql.sql.gz redis.rdb >SHA256SUMS)
echo "已备份 → ${OUT}（$(du -sh "$OUT" | cut -f1)，$(grep -c '^table' "$OUT/manifest.tsv") 张表，Redis $(awk -F'\t' '/^redis_keys/{print $2}' "$OUT/manifest.tsv") 个 key）"

# 保留最近 BACKUP_KEEP 份（只删本脚本生成的时间戳目录）
ls -1d "$BACKUP_DIR"/[0-9]*-[0-9]* 2>/dev/null | sort -r | tail -n +$((BACKUP_KEEP + 1)) | while read -r old; do rm -rf "$old"; echo "清理旧备份 $old"; done || true
