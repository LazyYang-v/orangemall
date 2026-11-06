# Phase 5 实施报告（库存流水 · TC 高可用 · 热点 SKU）

> 依据 `docs/PHASE4-REPORT.md` §5 遗留：「库存流水」「TC 高可用」「热点 SKU 全局锁排队」。
> 实施时间 2026-11-06。改动已部署到本机 compose 栈。演练时以 HA 方式运行两个 TC，结束后已恢复为默认的单 TC。所有数字都来自本机同一台 Docker Desktop（8 CPU / 8 GB），只用于版本间对比。

## 结论

| 遗留项 | 结果 | 证据 |
|---|---|---|
| 库存流水 | ✓ `stock_ledger` 按 `(order_id, sku_id, op)` 唯一记账：扣减、回补都按订单幂等，回补件数以扣减流水为准 | `StockLedgerTest` 5 例（真 MySQL 8 + Flyway，含 8 线程并发回补只生效一次），变异 2/2 被抓；Flyway 往返 product V1→V3；压测与演练都核对「DEDUCT = 订单数、RESTORE = 取消数」 |
| TC 高可用 | ✓（有边界）可选 `ha` profile 起第二个 TC，与第一个共用 `seata` 库；kill 一个 TC 后在途事务由另一个收敛，数据一致 | `bench/drills/seata-ha.sh`，见 §2。恢复时间受 Seata 2.0 客户端写死的 10s 连接超时限制，不是零中断 |
| 热点 SKU | 定位清楚，**吞吐没有显著提升**：下单分支重排后热点 SKU p95 / max 变好；锁重试参数 A/B 无显著差异，保持默认 | `bench/hot-sku.sh`，见 §3 |

演练中还发现并修复了一个**从第一版就存在的 AT 写隔离缺陷**（§2.2）：关单、退款回补和后台改库存不在全局事务里，直接改 `sku` 行。
如果此时同一行上有一个正在回滚的下单全局事务，它的 undo 会因为行已被改动而失败，最终进入 `TimeoutRollbackFailed`，全局锁永远不释放，这个 SKU 从此下不了单。

## 1. 库存流水（`mall_product.stock_ledger`，V3）

```text
deduct(orderId, items)                      @Transactional，在下单全局事务里是最后一个 AT 分支
  for sku in sorted(items)
    INSERT ledger(orderId, sku, DEDUCT, n)  重复 → DuplicateKey → 这个 SKU 已扣过，跳过
    UPDATE sku SET stock=stock-n … WHERE stock>=n
      影响 0 行 → 3001，本地事务回滚（流水一起回滚）

restore(orderId, items) → 实际回补的 SKU 数  @GlobalLock + @Transactional，不在全局事务里
  qty = 该订单的 DEDUCT 流水；没有任何 DEDUCT 流水 → 视为 V3 之前的历史订单，按请求件数
  for sku in sorted(items)
    SELECT … FOR UPDATE                     先等全局锁，再拿行锁（§2.2）
    INSERT ledger(orderId, sku, RESTORE, qty)  重复 → 已回补，跳过
    UPDATE sku SET stock=stock+qty …
```

- **调用方改动**：
  - `/inner/stock/deduct`、`/inner/stock/restore` 的入参改为 `StockOpReq(orderId, items)`。
  - 下单、关单（order）、退款（pay）三处已同步。
- **流水插入用普通 INSERT**，不用 `INSERT IGNORE`，因为 Seata AT 解析器对 IGNORE 的支持不完整。重复靠唯一键报 `DuplicateKeyException` 来识别。
- **关单回补改为可重试**：
  - 原来 `close()` 在 CAS 成功后只调用一次 restore，失败就只写一条日志。订单已经关闭，再没有别的路径会回补，于是库存就永久少了。
  - 现在先同步试 3 次（间隔 0.5s / 1.5s，覆盖 order-submit 的 10s 全局超时）。流水保证「超时但其实成功了」的那次也不会多补。
  - 同步仍失败就登记到 Redis 延迟集合 `order:restore:retry`，由定时任务（5s 一轮）按 5s / 15s / 30s / 60s 继续重试，共约 2 分钟。登记在 Redis，进程重启不丢。
    同步阶段不再拉长，是为了不让用户取消请求和 MQ 消费线程长时间阻塞。
  - 后台重试也耗尽才写 `ALERT` 运维日志（`[关单补偿失败] … 需人工按 stock_ledger 核对`）。
  - 测试：`OrderCloseTest` 新增 4 例（同步重试成功、转入后台、后台重试成功、后台耗尽告警），两轮各 2 个变异都被抓住。
    另外在真实栈上暂停 mall-product 容器 25s 再取消订单：1 单转入后台，恢复后补齐，库存复原，没有告警。
  - 后台这一层是 final3 演练暴露出来的（§2.3）：TC 故障切换期间孤儿事务占着热点 SKU 的全局锁，同步 3 次（约 14s）没有等到锁释放。
- **顺带修复**：`buildLines` 原来按请求条目遍历。同一 SKU 在请求里出现两次时，会生成两条明细，每条都带合并后的件数，库存因此会多扣。现在改为按合并后的 SKU 遍历。

## 2. TC 高可用

### 2.1 部署

- **compose `seata-2`**（profile `ha`，默认不启动，约 330MB）：
  - 用同一镜像，与 `seata` 共用 environment 锚点。
  - `SERVER_NODE` 分别为 1 / 2。它是雪花算法的 workerId，共用一个库时必须不同，否则 XID 可能撞号。
  - 不映射宿主机端口。
- **客户端**：
  - `SEATA_ADDR=${SEATA_ADDR:-seata:8091}`，可以在 `.env` 覆盖（见 `.env.example`）：
    ```bash
    # backend/deploy/.env
    SEATA_ADDR=seata:8091;seata-2:8091
    docker compose --profile app --profile ha up -d --wait
    ```
  - 4 个 Seata 客户端（order / product / user / marketing）都会连接列表里的全部 TC。
- **客户端参数**（4 个服务的 `application.yml`，都可以用环境变量覆盖）：

| 参数 | 默认 → 现在 | 原因（演练实测） |
|---|---|---|
| `seata.client.load-balance.type` | `XID` → `RandomLoadBalance` | XID 负载均衡会把一个全局事务的所有请求发回发起它的 TC。那台 TC 被 kill 后，在途事务的回滚上报全部打到死节点（`Failed to report global rollback … Retry Countdown`），全局锁只能等超时才释放。db 模式下任意 TC 都能处理任意 XID，改成随机后，回滚重试会落到存活的 TC |
| `order-submit` 全局事务超时 | 60s → 10s | 孤儿事务持有热点 SKU 的全局锁，同一 SKU 的新单全部 `Global lock acquire failed`，直到超时回滚。压测中提交 p95 ≈ 1.3s、最慢 ≈ 3s |
| `seata.transport.rpc-rm/tm-request-timeout` | 15s / 30s → 5s | TC 宕机那一刻已发出的请求永远收不到响应，只能等满超时；扣库存分支此时还持有 SKU 行锁 |

- **无法配置的部分**：
  - Seata 2.0 客户端的 Netty 连接超时写死为 10s（`NettyClientConfig.connectTimeoutMillis`）。
  - `acquireChannel` 按地址加锁串行。
  - 存活列表每 10s 刷新一次。
  - `docker kill` 之后容器 IP 直接消失，没有任何一端回 RST。所以 kill 之后约 10~20s 内，选到死节点的请求会卡在建连上。线程快照里 order 有 14 个线程卡在 `NettyClientChannelManager.acquireChannel`。
  - 这是本方案的恢复时间下限。要彻底消除，需要换注册中心（Nacos / Raft 模式能感知下线），或升级到能配置连接超时的版本。

### 2.2 演练中发现的缺陷：非全局事务写 sku 行 → dirty undo → 全局锁永久占用

**现象**：一次演练与上一轮演练的取消阶段重叠。演练结束后 `seata.global_table` 留下 1 条 `status=14`（TimeoutRollbackFailed）。它在 `sku 100101` 上的全局锁一直不释放，该 SKU 之后的下单全部失败。商品服务日志如下：

```
branchRollback failed … reason:[Branch session rollback failed because of dirty undo log,
please delete the relevant undolog after manually calibrating the data.]
```

**根因**：
- `StockService.restore`（关单 / 退款调用）和 `ProductService.updateSku`（后台改价 / 改库存）都不在全局事务里，直接 UPDATE `sku`，完全不看 AT 全局锁。
- 一个下单全局事务的扣库存分支已在本地提交，但全局未决（这里是因为发起它的 TC 被 kill）。如果此时有人取消同一 SKU 的另一张订单，回补就会改这一行。
- 之后这个全局事务回滚时，undo 校验发现当前行与 after-image 不一致，于是拒绝回滚。
- marketing（`CouponWriter`）和 user（`RiskService`）早就加了 `@GlobalLock`，唯独商品漏了。

**修复**：
1. `restore`、`updateSku` 加上 `@GlobalLock`（restore 最多等 4s，调用方 Feign 读超时 5s）。
2. **改行前先 `SELECT … FOR UPDATE`**（`SkuMapper.lockById`）。只加注解还不够，第一次复测时出现了互等：
   - restore 先 UPDATE 拿到 InnoDB 行锁，提交时才检查全局锁，于是一边持有行锁一边等全局锁。
   - 持有全局锁的那个孤儿事务回滚时恰恰要改这一行，两边互等到超时。
   - 这一轮 2 单关单回补 3 次都失败，触发了新加的 ALERT。
   - Seata 的 `SelectForUpdateExecutor` 在保存点之后检查全局锁，被占时回滚到保存点（放掉行锁）再等待，不会挡住回滚。
3. 关单回补重试 + 告警（见 §1）。

**数据校准**（只涉及演练产生的数据）：
- 卡住的事务：按 undo 语义手工回退。库存 +1，删除它的 DEDUCT 流水、undo_log 和 TC 三张表里的行。
- ALERT 的 2 单（final1），以及 final3 中同步重试耗尽的 1 单（加后台重试之前）：按 DEDUCT 流水补记 RESTORE，并加回库存。
- 校准后 SKU 100101 库存回到 577，该 SKU 的 DEDUCT = RESTORE（5053 / 5053）。

### 2.3 演练结果

`bench/drills/seata-ha.sh` 的场景如下：
- 20 个用户持续下单同一热点 SKU。同时有后台取消器每秒取消刚下的订单，让关单回补与在途 / 孤儿事务争同一行。
- 第 8s `docker kill seata`，12s 后再拉起。
- 判定条件：
  - kill 后 20s 起零失败；
  - 失败只能是 503，不出现 500；
  - TC 表清空，没有回滚失败的会话；
  - 库存减少 = 订单数 − RESTORE，DEDUCT = 订单数；
  - 取消全部演练订单后库存复原。

每一轮的原始数据都在 `bench/results/raw/seata-ha-<轮次>.txt`。表里按时间顺序列出，包括失败的轮次，因为本节的每一项参数都是从它们里调出来的。
「最后一次失败」是相对 kill 时刻的大致时间。

| 轮次 | 配置 | 杀 | 成功率 | 最后一次失败 | 结论 |
|---|---|---|---|---|---|
| xid | 默认：XID 负载均衡，全局超时 60s | seata | 51.3% | 约 +26s（到压测结束都没恢复） | ✗ 回滚上报全部发往死节点，热点 SKU 全局锁等 60s 超时 |
| random-10s | 随机负载均衡 + 10s 全局超时 | seata | 56.7% | 约 +17s | 恢复了，但卡在 RPC 15s / 30s 超时 |
| probe | 同上 + RPC 超时 5s | seata | 89.2% | 约 +14s | ✓，但它的后台取消与下一轮重叠了 |
| rpc5s | 同上（被上一轮污染） | seata | 33.3% | 约 +22s | ✗ **dirty undo → status 14，全局锁永久占用**（§2.2） |
| final1 | 加 `@GlobalLock`（还没有先 `SELECT … FOR UPDATE`） | seata | 37.3% | 约 +21s | ✗ restore 与孤儿事务回滚互等，2 单回补耗尽、ALERT |
| final2 | 加先锁行 | seata | 83.8% | +16s | ✓ |
| final3 | 同上 | seata | 68.3% | +20s | 数据一致，但 1 单关单回补的同步 3 次（约 14s）都没等到锁，ALERT → 加后台重试（§1） |
| final4 | 同上 | seata-2 | 91.3% | +14s | ✓ |
| **final5** | **最终版**（加关单回补后台重试） | seata | 85.0% | +14s | ✓ 199 单，RESTORE 30，没有回补转入后台，无告警 |
| **final6** | 最终版 | seata-2 | 89.8% | +14s | ✓ 263 单，RESTORE 35，没有回补转入后台，无告警 |

最终版的逐 2 秒分布（final6，成功 / 失败）：

```text
-8:25/0  -6:38/0  -4:38/0  -2:28/0  +0:5/1  +4:0/9  +8:0/2  +12:1/18  +14:23/0  +16:23/0  +18:31/0  +20:42/0
```

- kill 后约 14s 的不可用窗口里，失败全部是 503「系统繁忙，请稍后重试」，没有 500，也没有一单数据不一致。
- 成功率随轮次波动，是因为 kill 落在哪些在途事务上是随机的，最后一次失败的时间（+14~20s）才是稳定的指标。
- 对照：同一参数下单 TC 重启演练（`seata-restart.sh`，没有第二个 TC）成功 134 / 263（51%）；两个 TC 时是 85~90%。窗口缩短了但不是零，原因见 §2.1「无法配置的部分」。

## 3. 热点 SKU

`bench/hot-sku.sh` 让 N 个用户（默认 20）在同一个 SKU 上持续 preview → submit。它报告成功吞吐、提交耗时分位和结果码分布，并核对库存、流水与订单一致；结束时取消全部订单，库存复原。

**排队在哪里**：压测中抓 mall-product 的线程快照：
- 14 个线程卡在 Seata 前镜像查询 `SELECT … FOR UPDATE`（`BaseTransactionalExecutor.buildTableRecords`），排队等 InnoDB 行锁。
- 1 个线程在 `branchRegister`，等前一单全局提交后释放全局锁。

所以同一 SKU 上的每一单，都要串行等「前一单 Feign 返回 → TM 全局提交 → TC 释放锁 → 本单重试拿锁」这一段。

**改动**：
- `submit()` 的分支重排：锁券 → 扣积分 → 建单（主表 + 明细放进同一本地事务，原来每条 INSERT 自动提交、各是一个分支）→ 扣库存放最后，缩短热点行的全局锁持有时间。
- 开分支前先做快照库存预检，明显不够时直接失败，不再「扣了一半再全局回滚」。

**数据**（20 用户 × 20s，SKU 100101）：

| 场景 | 成功吞吐 | p50 | p95 | max |
|---|---|---|---|---|
| 改动前（冷启动后） | 13.1 单/s | 1267ms | 2033ms | 3180ms |
| 分支重排后（冷启动后） | 13.2 单/s | 1287ms | 1753ms | 2775ms |
| 预热后，锁重试默认 10ms × 30（3 轮） | 18.5 / 16.3 / 18.1，均值 17.6 | 922–977ms | 1235 / 1896 / 1245ms | — |
| 预热后，锁重试 2ms × 150（3 轮，与上一行交替跑） | 19.1 / 17.7 / 17.5，均值 18.1 | 923–985ms | 1131 / 1253 / 1385ms | — |
| 对照：20 用户各下不同 SKU | 20.2–23.0 单/s | 527–724ms | 972–1001ms | — |

- **吞吐**：分支重排没有带来可测的提升（13.1 → 13.2），只降低了尾延迟。
- **锁重试**：2ms 比默认 10ms 高约 3%，在噪声范围内（第 3 轮还反过来了），换来 3~5 倍的 TC 重试 RPC，不值得。保持默认，保留环境变量 `SEATA_LOCK_RETRY_INTERVAL` / `SEATA_LOCK_RETRY_TIMES`。
- **之前的判断要纠正**：「热点锁让吞吐少约 40%」来自冷启动和预热后的错误对比。预热后热点 SKU（≈ 17.6 单/s）只比分散 SKU（≈ 21 单/s）低约 15%。普通下单的主要上限仍是每单 5 次同步 TC RPC。
- **真正的热点商品**（大促爆款）应该走秒杀链路（Redis 预扣 + MQ 异步落单），不走 AT 普通下单。

## 4. 回归

本机栈回到默认单 TC 之后统一跑了一遍：

- 后端 `mvn clean install` 39/39（Phase 4 是 30 例；新增 `StockLedgerTest` 5 例、`OrderCloseTest` 4 例）。
- Flyway 往返 6/6（product 为 V1→V3）。
- Playwright 31/31（`--retries=0`）。
- `mobile-audit`（`STRICT=1`）共 30 页，横向溢出全部 0px。
- 单 TC 重启演练 `seata-restart.sh` ✓：134 单，库存 -134，undo_log 回到基线，恢复后下单成功，取消后库存复原。
- 两个 TC 的演练 `seata-ha.sh`：最终版 final5（杀 seata）、final6（杀 seata-2）✓，见 §2.3。
- 改动的服务：product、order 重新构建并部署；user、marketing 只改了 `application.yml`（Seata 客户端参数），也已重建。

## 5. 遗留与建议

- **TC 恢复时间下限约 10~20s**：由 Seata 客户端写死的连接超时和存活列表刷新周期决定（§2.1）。生产环境建议把 TC 注册到 Nacos，或者在 TC 前加一层能主动拒绝连接的 L4 负载均衡，让死节点立即返回 RST，而不是让连接超时。
- **库存对账**：现在有了流水，可以做一个定时对账：「已关闭 / 已退款订单的 DEDUCT 没有对应 RESTORE」「DEDUCT 没有对应订单」都报警。本阶段两次人工校准用的正是这条口径。
- **关单回补的最终兜底**：同步 3 次加后台约 2 分钟的重试，覆盖了 TC 故障切换的窗口。超过 2 分钟的故障（比如 mall-product 长时间不可用）仍靠 ALERT 加人工处理，上面的定时对账可以把这一步自动化。
  - 后台重试是「先从延迟集合 ZREM 领取，再执行」。实例恰好在领取之后、重新登记之前崩溃，这一单会漏掉，同样要靠对账兜底。
- **本机默认仍是单 TC**：`.env` 没有改，HA 只在演练时用 shell 环境变量开启。需要常驻 HA 时，在 `.env` 中设置 `SEATA_ADDR` 并加 `--profile ha`。
