#!/usr/bin/env bash
# OM-013 演练 B「在途计数泄漏」：模拟 Lua 已扣减但消息丢失（在途 +K、Redis 库存 −K，主题无积压）
# → 巡检判定泄漏 → 告警 + 自动重置在途并以 DB 为准回补 Redis 库存。
# 用法：bench/drills/inflight-leak.sh [activityId]   耗时约 stuckSeconds + 1 分钟
source "$(dirname "$0")/common.sh"
AID=${1:-$(ongoing_aid)}
K=${K:-2}
T0=$(now_iso)
DB=$(sql "SELECT stock FROM mall_seckill.seckill_activity WHERE id='$AID'")
echo "活动 $AID  注入前 Redis=$(rc GET seckill:stock:$AID) DB=$DB 在途=$(rc GET seckill:inflight:$AID)"
rc INCRBY "seckill:inflight:$AID" "$K" >/dev/null
rc DECRBY "seckill:stock:$AID" "$K" >/dev/null
echo "已注入：在途=$(rc GET seckill:inflight:$AID) Redis=$(rc GET seckill:stock:$AID)"
echo "等待泄漏告警与自动重置…"
wait_alert "在途重置] $AID" "$T0" 420
R=$(rc GET seckill:stock:$AID); I=$(rc GET seckill:inflight:$AID)
echo "重置后：在途=$I Redis=$R DB=$DB"
[ "$I" = "0" ] && [ "$R" = "$DB" ] && echo "✓ 泄漏已自动修复，Redis 与 DB 一致" || { echo "✗ 修复结果不符"; exit 1; }
