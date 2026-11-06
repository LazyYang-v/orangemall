#!/usr/bin/env bash
# OM-019 演练「投毒消息」：向秒杀主题投递非 JSON / 非对象 / 缺字段三类消息
# → 主消费者转 DLT（不阻塞分区）→ DLT 监听留档到 seckill_dead_letter + ALERT 告警 → 后台可查、可标记处理。
# 用法：bench/drills/poison.sh   耗时约 10 秒
source "$(dirname "$0")/common.sh"
T0=$(now_iso)
BEFORE=$(sql "SELECT COUNT(*) FROM mall_seckill.seckill_dead_letter")
MAXID=$(sql "SELECT COALESCE(MAX(id),0) FROM mall_seckill.seckill_dead_letter")
TAG="drill-$(date +%s)"
printf '%s\n' "not-json-$TAG{{" "[\"$TAG\"]" "{\"activityId\":\"\",\"userId\":\"$TAG\"}" |
  docker compose exec -T kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server kafka:9092 --topic seckill-order
sleep 6
echo "新增死信："
sql "SELECT id, reason, LEFT(payload, 40), CONCAT(src_partition,'-',src_offset) FROM mall_seckill.seckill_dead_letter WHERE id > $MAXID ORDER BY id"
AFTER=$(sql "SELECT COUNT(*) FROM mall_seckill.seckill_dead_letter")
wait_alert "[死信]" "$T0" 30
echo "后台待处理死信：$(api GET /admin/seckill/health | jq_ 'd["data"]["deadLetters"]')"
[ $((AFTER - BEFORE)) -eq 3 ] && echo "✓ 3 条投毒消息全部留档，主消费者未阻塞" || { echo "✗ 期望新增 3 条，实际 $((AFTER - BEFORE))"; exit 1; }
