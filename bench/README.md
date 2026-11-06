# bench/ — 秒杀压测与故障演练（OM-025 / OM-013 / OM-019）

可复现的压测脚本、数据准备、结果核对与故障演练。所有脚本在本机 docker compose 栈（`backend/deploy`）上运行，**k6 用 docker 镜像，无需本机安装**。

```
bench/
├── k6/
│   ├── lib.js              公共：登录、活动选择、Result 解析
│   ├── sessions.js         基线 1：场次列表（匿名读）           目标 ≥ 1500 QPS，p95 < 200ms
│   ├── submit-reject.js    基线 2：伪造 path 直刷（拒绝路径）     目标 ≥ 3000 QPS，p95 < 100ms
│   └── seckill-e2e.js      基线 3：5000 个真实用户完整抢购链路   目标 落单 ≥ 200 单/s，零超卖
├── sql/seed-bench-users.sql  5000 个压测用户 17700000000~17700004999（密码 123456）+ 默认地址
├── prepare.sh              为 e2e 准备活动：等静默（消费者运行、积压/在途归零）→ 设库存、清上一轮压测用户痕迹、预热
├── verify.sh               e2e 后以 DB/Redis 为准核对「不超卖」
├── run.sh                  docker 版 k6 运行器，摘要写入 results/raw/
├── chain-throughput.sh     纯落单吞吐：暂停消费者积压 1000 单 → 恢复计时到在途归零 → verify
├── profile-drain.sh        落单途中对 seckill/order 抓线程快照（定位瓶颈用）
├── hot-sku.sh              普通下单热点 SKU：N 用户同一 SKU 持续 preview→submit，吞吐 / 耗时分位 / 结果码 + 库存·流水·订单核对
├── drills/                 故障演练（告警可测）
│   ├── poison.sh           投毒消息 → DLT 留档 + 告警（~10s）
│   ├── inflight-leak.sh    在途计数泄漏 → 告警 + 自动重置（~4min）
│   ├── consumer-down.sh    消费者宕机 → 滞留告警（不重置）→ 恢复 → 恢复告警（~5min）
│   ├── order-down.sh       建单服务宕机 → 重试耗尽 → 补偿（库存回补/成交删除/FAILED）+ EXHAUSTED 死信（~1.5min）
│   ├── seata-restart.sh    普通下单压测中重启 Seata TC（db 模式）→ 在途事务收敛、库存 = 订单、无 500（~1.5min）
│   └── seata-ha.sh         两个 TC 之一 docker kill（需 HA 部署）→ +20s 起零失败、另一 TC 收敛、关单回补无遗漏（~2min）
└── results/                基线报告（YYYY-MM-DD.md）与原始输出 raw/（最新：[2026-11-06.md](results/2026-11-06.md)）
```

## 一次完整流程

```bash
cd backend/deploy && docker compose ps            # 栈已启动（含 --profile app）
# 0. 压测用户（幂等）
docker compose exec -T mysql mysql -uroot -proot --default-character-set=utf8mb4 < ../../bench/sql/seed-bench-users.sql
cd ../..

# 1. 读基线 / 拒绝路径基线
bench/run.sh sessions.js RATE=1500
bench/run.sh submit-reject.js RATE=3000 USERS=2000

# 2. 真实链路：选一个进行中的活动（后台「秒杀活动」页或 /api/seckill/sessions）
AID=S1793916000000-100501
bench/prepare.sh $AID 1000                        # 库存设为 1000 并预热
bench/run.sh seckill-e2e.js AID=$AID USERS=5000 VUS=1000
bench/verify.sh $AID                              # ✓ 无超卖 / ✗ 并退出码 1

# 2b. 纯落单链路吞吐（不含 k6 登录/轮询噪声）
bench/chain-throughput.sh $AID 1000

# 3. 故障演练
bench/drills/poison.sh
bench/drills/inflight-leak.sh
bench/drills/consumer-down.sh
bench/drills/order-down.sh
bench/drills/seata-restart.sh                     # 默认取库存最多的在售 SKU；结束时取消演练订单，库存回到原值

# 4. 普通下单热点 SKU / TC 高可用（Phase 5）
TAG=after bench/hot-sku.sh                        # 原始数据 results/raw/hot-sku-$TAG.txt
(cd backend/deploy && SEATA_ADDR='seata:8091;seata-2:8091' docker compose --profile app --profile ha up -d --wait)
SEATA_ADDR='seata:8091;seata-2:8091' TAG=run1 bench/drills/seata-ha.sh   # TC=seata-2 可改杀第二个
(cd backend/deploy && docker compose --profile app up -d --wait && docker compose stop seata-2)   # 回到默认单 TC
```

## 约定与注意

- **默认直连网关**（`BASE=http://mall-gateway:8080/api`，compose 内网），测的是服务本身；nginx 入口对 `/api/seckill/*/*/orders` 有 `seckill_ip 200r/s` 单 IP 限流，用 `BASE=http://nginx/api` 可验证该限流生效。
- 压测与被测服务共用同一台机器（Docker Desktop 8 CPU / 8 GB），数值是**同机基线**，用于版本间对比，不代表生产容量。
- `prepare.sh` 只改指定活动的 `total_stock` 并只清理压测用户（id `1770000000000000000`~`+4999`）的成交 / 结果 / path 计数，不碰真实用户数据；压测产生的待支付订单会在支付超时后被正常关单。
- e2e 是否超卖**只看 `verify.sh`**（DB/Redis 为准）：库存消耗 = `seckill_order` 行数 = 订单数，Redis/DB ≥ 0 且 Redis = DB − 在途，无一人多单。
- 一轮中途失败后直接重跑是安全的：`prepare.sh` 会先恢复消费者并等上一轮积压消费完，否则清理期间仍有旧消息落单，核对会出现「成交 > 总库存」的假超卖。
- 秒杀订单超时关单**不回补秒杀库存**（只有普通单回补商品库存），所以 verify 在关单前后结论一致。
- `seata-restart.sh` 用压测用户 4900~4919（需已有收货地址，`seed-bench-users.sql` 已建）；TC 不可用窗口内的提交应返回 503（可重试），
  不允许出现 500；`undo_log` 只统计 `log_status=0`，`log_status=1` 是 Seata 防悬挂标记（7 天清理），属预期。
- `seata-ha.sh` 期间每秒取消刚下的压测订单，让关单回补与在途 / 孤儿下单事务争同一 SKU 行；结束前会等关单回补的后台重试队列（Redis `order:restore:retry`）清空，并检查 mall-order 日志里没有「关单补偿失败」告警。
- 演练依赖 `SECKILL_INFLIGHT_STUCK_SECONDS`（默认 180s）；想更快可在 `.env` 调小后重启 mall-seckill。
