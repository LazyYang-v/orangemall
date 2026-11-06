# Phase 6 实施报告（库存对账 · 退款收尾补偿 · TC 注册到 Nacos）

> 依据 `docs/PHASE5-REPORT.md` §5 遗留：「库存对账」「关单回补的最终兜底」「TC 恢复时间下限」。
> 实施时间 2026-11-06。改动已部署到本机 compose 栈；TC 演练时以 HA 方式运行两个 TC，结束后恢复为默认的单 TC。
> 所有数字都来自本机同一台 Docker Desktop（8 CPU / 8 GB），只用于版本间对比。本轮整机负载偏高（load 6~8），热点 SKU 吞吐约为 Phase 5 的一半，
> 所以 §3 的每组对比都在同一时段内交替测量。

## 结论

| 项 | 结果 | 证据 |
|---|---|---|
| 库存对账 | ✓ 每 60s 以 `stock_ledger` 为准核对一个窗口：已关闭缺回补 → 自动补；普通单无扣减 / 扣减无订单 → ALERT | `StockReconcileServiceTest` 4 例 + `StockReconcileJobTest` 4 例 + `StockLedgerTest` 新增 1 例（真 MySQL），变异 3/3 被抓；真实栈上造一单「关了没补」→ 自动补回，造一条孤儿 DEDUCT → ALERT（§1.3） |
| 退款收尾丢失（新发现的缺陷） | ✓ 修复：售后单 DONE 之后的回补库存 / 订单 REFUNDED 失败可补偿 | `RefundSettleTest` 5 例，变异 3/3 被抓；真实栈上停掉 mall-product 再走完退款，恢复后约 1 分钟自动收尾（§2） |
| TC 恢复时间 | **部分改善**：TC 注册到 Nacos 后，**优雅停机**（滚动重启）零失败；**进程崩溃**仍约 +14s，与 file 注册相同 | `seata-ha.sh` 新增 `MODE=stop`，file / Nacos 各跑 kill 与 stop（§3）；采用为 `docker-compose.ha.yml` |

## 1. 库存对账（mall-order `StockReconcileService` / `StockReconcileJob`）

### 1.1 口径

```text
reconcile(from, to)
  ① 窗口内 closed_at 的 NORMAL 单（CLOSED / CANCELLED，4 张分表）
       每 500 单一批 → product /inner/stock/ledger/by-orders
       DEDUCT 有而 RESTORE 缺的 SKU → product.restore(orderId, 缺的 SKU × DEDUCT 件数)   按订单幂等，重复执行只补一次
         失败 → order:restore:retry（与关单回补同一后台重试队列）
  ② 窗口内 created_at 的 NORMAL 单，在 [from−60s, to+60s) 的 DEDUCT 里找不到 → ALERT「没有扣减流水（未占库存，可能超卖）」
  ③ [from, to) 的 DEDUCT 订单号，分表 + 归档表都查不到 → ALERT「扣减流水找不到订单（库存被占用）」，不自动处理
  任一查询达到上限（每分表 10000 / 流水 20000）→ ALERT「部分未核对」
```

- ① 兜住所有「回补丢了」的路径：同步 3 次 + 后台约 2 分钟都失败；后台重试 ZREM 领取后实例崩溃（Phase 5 遗留）；Redis 丢数据。
- ②③ 只告警：它们只可能来自缺陷或人工改库，自动回补会掩盖问题。
- ② 的流水窗口两侧放宽 60s：订单和流水各自用 DB 时钟写 `created_at`，两者相差不超过一个下单全局事务（10s）。
- 退款单不在①的范围：退款回补由 §2 的收尾补偿负责，对账只看关单。

### 1.2 调度

- `StockReconcileJob` 每 60s 一轮，Redis `SETNX order:stock-reconcile:since:lock`（240s）保证多实例只跑一份。
- 检查点 `order:stock-reconcile:since`（epoch ms）：
  - 首轮只把检查点设为 now，不回头核对上线前的历史订单（V3 之前的老订单没有流水，会被误报）。
  - 每轮最多 30 分钟，右端 = now − 5min（下单全局超时 10s、关单回补后台重试约 2 分钟，留足余量，不把在途的单当异常）；落后时多轮追上，最多追 24h。
  - 对账抛异常时检查点不动，下一轮重跑同一窗口。
- 手动补跑：`POST /admin/orders/stock-reconcile?hours=24&settleSec=300`（admin，1 ≤ hours ≤ 168），返回 `Report`，不动检查点。
- 索引：order V5 `t_order_{0..3}(closed_at)`、`(created_at)`；product V4 `stock_ledger(op, created_at)`。都有 U 回滚脚本。

### 1.3 真实栈验证

- 2 小时窗口手动对账：核对关单 580、建单 412，三张清单都为空。
- **关了没补**：`docker stop mall-product` 后取消订单 377095049320267776，同步 3 次和后台重试都失败。
  启动 mall-product，清掉后台重试登记，模拟「重试领取后实例崩溃」。
  - 结果：对账自动补回，SKU 100101 库存 576 → 577，流水变为 DEDUCT + RESTORE，ops 日志「[库存对账] … 已自动补偿」。
  - 再跑一次没有任何动作，幂等。
- **孤儿扣减**：手工插入一条订单号 999000000000000003 的 DEDUCT，对账发出 ALERT「1 条扣减流水找不到订单」。验证后删除了该测试行。

### 1.4 演练里踩到的坑：`docker pause` 不等于服务不可用

第一次模拟「关了没补」用的是 `docker pause mall-product`。订单侧回补全部超时，但恢复后库存一件不少。
原因是暂停期间请求已经排在容器的 socket 上，unpause 后照常执行，调用方超时不代表对端没有成功。
这正是流水按订单幂等要防的场景，但用它来模拟「丢失」不对。之后改用 `docker stop` / `start`。

## 2. 退款收尾丢失（mall-pay `RefundService.settle`）

**缺陷**：退款回调先 CAS 售后单 REFUNDING → DONE 并更新支付单，再回补库存、把订单改为 REFUNDED。后两步任一失败
（mall-product / mall-order 短暂不可用、Feign 5s 读超时）都会抛给渠道回调；渠道重放回调时，售后单已经是 DONE，被幂等吞掉。
结果是钱退了，订单永远停在 REFUND_APPLYING，库存不回补，也没有任何告警。

**修复**：

```text
applyRefundSuccess（渠道回调）
  CAS refund_order REFUNDING → DONE；payment 累加 refunded_amount
  try settle(r)  catch → ops「[完成延后] … 转后台补偿」，回调照常确认

settle(r)                                  两步都幂等，可以任意重放
  type ≠ EXCHANGE → product.restore(orderId, items)    按 stock_ledger 每单只补一次
  order.refundState(REFUND_APPLYING → REFUNDED)        已是 REFUNDED 也算成功
  CAS refund_order.settled_at（只写一次）→ ops「[完成]」+ WebSocket

PayJobs.settleDoneRefunds（每 60s，SETNX pay:refund:settle:lock）
  DONE ≥ 30s 且 settled_at 为空 → settle；成功记「[补偿] … 退款收尾已补齐」
  DONE 超过 10min 仍失败 → ALERT「[退款收尾失败]」，每单 24h 只发一次
```

- pay V4 新增 `refund_order.settled_at`，并把历史 DONE 单回填为 `done_at`。V3 之前的老订单没有流水，重放回补会按请求件数再补一次，所以不能重放。
- order 侧 `refundState`：目标为 REFUNDED 且当前已是 REFUNDED 时返回 false（视为成功），重放不报错。
- **真实栈验证**：用户对订单 377095398525435907 申请退款（售后单 377095410092179456），审批前 `docker stop mall-product`。
  1. 渠道回调到达，日志 `[完成延后]`；售后单 DONE，`settled_at` 为空，订单 REFUND_APPLYING，流水只有 DEDUCT。
  2. 启动 mall-product，约 58s 后 PayJobs 补齐：订单 REFUNDED，流水 DEDUCT + RESTORE，库存 577（回到退款前），日志 `[完成]` → `[补偿]`。

## 3. TC 注册到 Nacos

### 3.1 做法

- 不改代码，也不改服务的 `application.yml`：Spring 环境变量 `SEATA_REGISTRY_TYPE=nacos`、`SEATA_REGISTRY_NACOS_*` 覆盖 `seata.registry.type=file`。
  TC（seata-server 2.0 也是 Spring Boot）用同一组变量注册到 Nacos，分组 `SEATA_GROUP`、集群 `default`（对应客户端 `vgroup-mapping`）。
- 固化为叠加文件 `backend/deploy/docker-compose.ha.yml`：两个 TC + 4 个 Seata 客户端（order / product / user / marketing）。
  ```bash
  docker compose -f docker-compose.yml -f docker-compose.ha.yml --profile app --profile ha up -d --wait
  # 常驻：.env 里设 COMPOSE_FILE=docker-compose.yml:docker-compose.ha.yml、COMPOSE_PROFILES=app,ha
  ```
- 默认部署不变：单 TC、file 注册。
- `bench/drills/seata-ha.sh` 新增 `MODE=stop`（`docker stop -t 15`，SIGTERM 优雅停机，模拟滚动重启），默认仍是 `kill`。

### 3.2 演练数据

20 用户持续下单同一 SKU，后台每秒取消刚下的单；第 8s 停一个 TC，12s 后拉起。原始数据见 `bench/results/raw/seata-ha-<轮次>.txt`。

| 轮次 | 注册 | 停 | 成功率 | 失败 | 最后一次失败 | 结论 |
|---|---|---|---|---|---|---|
| Phase 5 final5 / final6 | file | kill | 85.0% / 89.8% | — | +14s | 基线 |
| nacos-kill | Nacos | kill seata | 46.7% | 56 | +18s | ✓ 判定通过，但服务刚重建、未预热 |
| nacos-kill2 | Nacos | kill seata | 62.7% | 31 | +14s | ✓ 与 file 相同 |
| file-stop | file | stop seata | 67.3% | 54 | 约 +21s | ✗ +20s 起仍有 16 次失败 |
| **nacos-stop** | **Nacos** | **stop seata** | **100%** | **0** | — | ✓ 252 单 |
| **nacos-stop-2** | **Nacos** | **stop seata-2** | **100%** | **0** | — | ✓ 310 单 |

逐 2 秒分布（成功 / 失败，相对停机时刻）：

```text
file-stop   +0:17/0  +2:15/8  +6:0/1  +8:0/5  +12:0/20  +18:0/4  +20:0/16  +22:4/0  +24:16/0
nacos-stop  +0:20/0  +2:18/0  +4:22/0 +6:19/0 +8:21/0  +10:17/0 +12:12/0 +14:14/0 +16:12/0 +18:12/0 +20:20/0
nacos-kill2 +0:3/8   +2:0/5   +6:0/3  +12:0/1 +14:0/14 +16:3/0  +18:6/0  +20:7/0
```

所有轮次都满足数据一致：TC 表清空、无回滚失败、undo_log 回到基线、DEDUCT = 订单数、取消后库存复原、无「关单补偿失败」告警。

### 3.3 为什么只对优雅停机有效

- **file 注册**：客户端的 TC 列表是写死的 `SEATA_ADDR`，停掉的 TC 一直在列表里，随机负载均衡仍会选中它，于是出现两段失败：
  - 停机后，容器 IP 消失，没有任何一端回 RST，请求卡在 Seata 2.0 写死的 10s 建连超时上；
  - 重启后、8091 开始监听之前（JVM 启动约 11s），连接被立即拒绝，`can not connect to services-server` → 503。
- **Nacos 注册**：TC 收到 SIGTERM 时先从 Nacos 注销，客户端收到推送后立刻摘掉它；重启后 TC 在端口监听之后才注册。
  所以客户端只会连到在册且可用的 TC，两段失败都没有了。
- **进程崩溃**：Nacos 2.x 的临时实例靠 gRPC 长连接判活，容器被 kill 后连接没有收到 FIN / RST，Nacos 迟迟察觉不到。
  nacos-kill 一轮里，被杀节点直到约 27s 后它重启、重新注册时才被摘除，比客户端自己 10s 一次的存活列表刷新还慢。
  恢复时间仍由客户端连接超时决定，与 file 注册相同。
- **正常路径无代价**：热点 SKU 压测（20 用户 × 20s，同一时段交替）file 9.2 / 10.1 单/s，Nacos 9.7 / 9.0 单/s，差异在噪声内。

## 4. 回归

- 后端 `mvn clean install` 53/53（Phase 5 是 39 例；新增 `StockReconcileServiceTest` 4、`StockReconcileJobTest` 4、`RefundSettleTest` 5、`StockLedgerTest` 1）。
- 变异（每个都让对应测试失败）：
  - 对账：去掉归档表查询、去掉流水窗口放宽、`missingRestore` 不排除已回补的 SKU；
  - 退款收尾：收尾失败照旧抛给渠道回调（即原缺陷）、换货也回补库存、告警去掉「每单只发一次」的 Redis 键。
- Flyway 往返 6/6（product V1→V4、order V1→V5、pay V1→V4）。
- Playwright 31/31（`--retries=0`）；`mobile-audit`（`STRICT=1`）30 页横向溢出全部 0px。
- 真实栈：§1.3、§2 的故障注入，§3.2 的 6 轮 TC 演练与 4 轮热点 SKU 对照，每轮结束库存都回到 577。
- 改动的服务：product、order、pay 重新构建并部署（Flyway 自动执行 product V4 / order V5 / pay V4）；
  TC 实验只用环境变量，没有重建镜像。结束后本机恢复为单 TC、file 注册，seata-2 停止，Nacos 里没有残留的 TC 实例。
- 没有重跑单 TC 重启演练 `seata-restart.sh`：本阶段没有改 Seata 相关代码与默认配置。
- TC 停机耗时：file / Nacos 注册下 `docker stop` 都约 4s 正常退出（143），在 compose 默认 10s 宽限期内。

## 5. 遗留与建议

- **TC 进程崩溃仍有约 14s 窗口**：要再缩短，需要 Seata 客户端能配置建连超时（2.x 后续版本），或在 TC 前加一层能主动拒绝连接的 L4 负载均衡。
  滚动发布已经可以做到零失败，前提是用 `docker stop` / SIGTERM，不要 kill。
- **对账只覆盖普通单**：秒杀单走 Redis + `seckill_order`，已有自己的一致性巡检（`backend-design.md` §3.2）。
- **扣减无订单只告警**：确认是缺陷后，人工按订单号调用 `/inner/stock/restore` 补回。以后可以给后台加一个带审批的按钮。
- **本机默认仍是单 TC**：`.env` 没有改，需要常驻 HA 时按 §3.1 设置。
