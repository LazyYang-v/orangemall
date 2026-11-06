# Phase 4 实施报告（数据库 · 测试 · 工程化）

> 依据 `docs/AUDIT-2026-11-06.md` Phase 4：「Flyway、Testcontainers 集成测试、CI 门禁（构建/测试/镜像）、Prometheus+Grafana、备份恢复脚本；
> DoD：CI 全绿（含迁移 up/down 往返）、备份演练一次」，并入 OM-017（Seata TC 单点）、OM-018（业务容器 healthcheck）、OM-020（监控）、
> Phase 3 遗留的运营后台 375px 横滑。实施时间 2026-11-06，改动均已部署到本机 compose 栈并回归。

## DoD 结论

| DoD | 结果 | 证据 |
|---|---|---|
| CI 全绿（构建 / 测试 / 镜像） | ✓ 本机按 CI 同样命令执行：`mvn clean verify` 30 例全过（含 Testcontainers MySQL 8 / Redis 7）、8 个服务镜像构建成功、前端 `vue-tsc + vite build` 通过。GitHub 上首跑需推送后确认 | `.github/workflows/ci.yml`（backend / migrations / frontend 三个 job） |
| 迁移 up/down 往返 | ✓ 6 个库逐版本 up → 逐版本 down 复原 → 再 up 与首次一致 | `scripts/db/migration-roundtrip.sh`（CI `migrations` job） |
| 备份演练一次 | ✓ 备份 `backups/20261106-180929`（1.5M：7 个库 43 张表 + Redis 1682 key，校验和一致）→ 一次性容器恢复，43 张表行数逐表一致、Redis key 数一致；临时容器已销毁，未触碰线上 | `scripts/backup.sh`、`scripts/restore.sh --drill` |

## 1. 交付内容

| 编号 | 内容 | 关键改动 | 验证 |
|---|---|---|---|
| Flyway | 每个服务管理自己的库 | 6 个有库服务各自 `db/migration/V1__schema.sql`（原 `01-schema.sql` 按库拆分）+ `V2__seed.sql`（`gen-seed.ts --write` 生成后冻结）+ 增量；`db/rollback/U<n>__*.sql` 回退脚本，往返脚本强制每个 V 都有 U。`baseline-on-migrate` + `baseline-version=2` 接管已有库；Flyway 走独立连接（不经 Seata 数据源代理）。MySQL init 只剩建库 `01-databases.sql` 和 TC 表 `02-seata.sql` | 线上 6 库 baseline v2，order / pay 应用 V3（字段注释对齐）；线上库结构与空库全新迁移逐行一致（779 行 dump 相同）；往返 6/6 |
| 测试 | 单测 + Testcontainers | `SeckillDeductBatcherTest`（MySQL 容器 + Flyway 真建表：400 人抢 100 件恰好 100 单、重投幂等、库存不足回退逐单）、`SeckillLuaScriptsTest`（Redis 容器：stock / finish / guard 三个 Lua 的并发不超卖、一人一单、补偿、令牌桶）、`PricingTest` 12 例、`SandboxSignerTest` 3 例、`OrderCloseTest` 3 例、`GlobalExceptionHandlerTest` 4 例；Testcontainers 1.21.4（Docker Engine 29 要求 API ≥ 1.40）；无 Docker 时自动跳过 | 30/30；批量扣减用例做过变异校验（改坏 `decrStockBy` 参数即失败），`OrderCloseTest` 对缺陷代码变异校验 2/3 失败 |
| CI 门禁 | `.github/workflows/ci.yml` | backend：`mvn clean verify` → 测试汇总表写入 Step Summary → `docker compose --profile app build`；migrations：往返脚本；frontend：类型检查 + 构建；e2e.yml 改用 `up --wait` 等 healthcheck | 本机等价执行全过 |
| OM-020 | Prometheus + Grafana | 各服务 actuator 独立管理端口 8558（不映射宿主机、网关路由不到），暴露 health / info / prometheus，统一 `application` 标签，HTTP 与网关路由开直方图；秒杀业务指标 `SeckillMetrics`（漏斗计数、在途、Kafka 积压、死信、消费者状态，5s 采样，抓取不放大 Redis/Kafka 访问）；**错误率指标 `mall_api_errors_total{kind,code}`**（本项目 HTTP 恒 200，5xx 永远为空，改按业务码分类计数）；compose `monitor` profile：Prometheus（127.0.0.1:19090，7 天保留）+ Grafana（127.0.0.1:13000，匿名只读，预置「橙选商城」看板 20 个面板：总览 / 网关 / 服务 / 秒杀） | 9 个 target 全部 UP；演练流量下看板各面板出数（截图见 §3） |
| 备份恢复 | `scripts/backup.sh` / `restore.sh` | 备份：全部 `mall_*` 库 + `seata` 库 `--single-transaction` 一致性快照、Redis `--rdb`，`manifest.tsv`（逐表行数、Redis key 数）+ `SHA256SUMS`，保留最近 7 份。恢复：`--drill` 在一次性 tmpfs MySQL / Redis 中恢复并逐表比对；`--into-live --yes` 才覆盖本机库（先自动再备份一次，Redis 不回灌线上） | 两次演练均通过（早间 33 表 / 1281 key；收尾 43 表 / 1682 key，新增 Flyway 历史表与 seata 表） |
| OM-018 | 业务容器 healthcheck + 启动编排 | 各服务 readiness（Spring 就绪 + 已注册 Nacos）healthcheck；依赖链 infra healthy → 业务服务 healthy → 网关 healthy → Nginx；Seata、Nginx 自带 healthcheck | 3 轮冷启动（停掉 8 个服务 + Nginx → `up -d --wait`）：每轮约 50s 全部 healthy，就绪后立即 200 个请求（登录 / 商品 / 券 / 场次），3 轮共 600 个请求 0 失败 |
| OM-017 | Seata TC db 模式 + 重启演练 | TC `store.mode=db`（全局会话 / 分支 / 全局锁落 MySQL `seata` 库，`02-seata.sql`），自建镜像补 MySQL 8 驱动（官方镜像驱动不在 classpath）；`bench/drills/seata-restart.sh`：20 个用户持续普通下单，第 6s 重启 TC | 见 §2.2，三项一致性断言通过 |
| 后台 375px | 运营后台移动端 | ≤900px 侧栏变顶部横滑菜单条、宽表格在卡片内横向滚动、多列网格折单列（KPI 两列）；`mobile-audit.mjs` 增加 11 个后台页面，`STRICT=1` 有溢出即失败 | 商城 19 页 + 后台 11 页横向溢出全部 0px（Phase 3 时后台 658–759px） |

## 2. 本阶段发现并修复的问题

### 2.1 【严重】关单状态从未改变 → 超时扫描每分钟重复回补库存（自首个版本起）

- **现象**：Seata 演练中 SKU 100101 库存持续上涨（577 → 579 → 580 → 583），且「库存减少 76 件 ≠ 77 单」。
- **根因**：`OrderService.close()` 调用 CAS 时把 `OrderStatus.PENDING_PAYMENT` 当作**目标状态**传入
  （`closeIfPending(src, id, PENDING_PAYMENT, now, reason)`，应为调用方给的 `CLOSED` / `CANCELLED`）。
  SQL 是 `SET status = #{status} … WHERE status = 'PENDING_PAYMENT'`，于是订单只写了 `closed_at / close_reason`，状态仍是待支付：
  - 每 60s 的超时兜底扫描（`Jobs.sweepOverdue`）会把同一批订单再「关」一次，**每次都回补库存**（积分、优惠券同理）；
  - 已取消订单仍可再次取消、仍可支付；前台 / 后台仍显示为待支付。
- **影响面（本机库）**：14,084 张「已关单但待支付」的订单，其中秒杀单 14,005 张（秒杀单关单不回补商品库存）、普通单 79 张；
  均未使用积分 / 优惠券，所以重复回补只影响普通单商品的库存。Seata 演练的 76≠77 也是它造成的：演练窗口内一张旧的普通僵尸单（同一 SKU）每分钟被扫描回补 1 件。
- **修复**：传入正确目标状态，并拒绝非终态参数；新增 `OrderCloseTest`（用桩模拟真实 CAS 语义：取消后再来 5 次超时关单，库存 / 积分只回补 1 次）。
  数据修复为 `mall-order` Flyway `V4__repair_zombie_closed_orders.sql`（按 `close_reason` 改为 `CANCELLED` / `CLOSED`，幂等），
  任何已部署环境升级即自动修正；本机库已修复，修复后僵尸单 0 张。
- **未能还原的部分**：被重复回补抬高的库存没有流水可以精确倒推（库存表只有当前值）。本机演示数据里 SKU 100101 已恢复到演练前的值；
  建议后续给库存加扣减流水（按订单号幂等记账），见 §5。

### 2.2 Seata TC 重启：一致性通过，失败请求从 500 改为可重试的 503

| 轮次 | 在途全局事务 | 提交结果 | 库存变化 = 订单 | TC 表 / 待处理 undo_log | 结论 |
|---|---|---|---|---|---|
| 1（修复关单前） | 37 | 135 成功 / 306 × 500 | 577→442，135 单 | 0 / 0 | 一致；但 TC 不可用窗口内用户看到「服务器开小差了」 |
| 2（关单修复 + 503 映射） | 26 | 54 成功 / 341 × 503 | 577→523，54 单 | 0 / 1 | 剩 1 行是 `log_status=1` 防悬挂标记（见下） |
| 3（演练脚本口径修正） | 21 | 129 成功 / 425 × 503 / 1 超时 | 577→448，129 单 | 0 / 0（标记 1） | ✓ 全部断言通过；取消演练订单后库存回到 577 |

- 失败请求全部落在 TC 不可用的约 12 秒窗口内，原因是 `can not register RM … can not connect`。
  TM 上报全局回滚失败的事务，在 TC 恢复后由超时回滚兜底，三轮都收敛到 TC 表 0 行。
- `GlobalExceptionHandler` 沿异常链识别 Seata 框架异常和被包装的 Feign 失败，返回 `503 系统繁忙，请稍后重试`，不再是 500。
  演练脚本把「出现 500」列为失败条件。
- `log_status=1` 的 undo_log 是 Seata 的防悬挂标记：TC 恢复后回滚一个本地事务从未提交成功的分支时，RM 写下这条标记，阻止迟到的提交。
  它没有回滚数据，按 7 天清理周期删除，属于预期行为。演练只统计 `log_status=0`。

### 2.3 其他

- macOS bash 3.2 下 `$var` 紧跟中文会被当成变量名的一部分，脚本里的变量统一写成 `${var}`。数组 `[-1]` 和 `head -n -N` 也改成了兼容写法。
- `docker cp` 无法写入 tmpfs 挂载，恢复演练里的 Redis 改为 `docker exec -i … cat >` 管道写入。
- 通过网关访问 `/api/users/../actuator/prometheus` 等路径都返回业务 404：actuator 只在管理端口 8558 上，从网关访问不到。

## 3. 监控看板

Grafana「橙选商城」看板（127.0.0.1:13000，匿名只读）。在 Seata 演练流量下，总览、网关、服务、秒杀四组面板都有数据：
网关 QPS / P95、服务在线 8、按路由 QPS 和 P95、各服务 HTTP P95、JVM 堆、Hikari 活跃连接、秒杀漏斗 / 在途 / Kafka 积压。
「系统错误率」和「各服务异常」面板改用 `mall_api_errors_total`。原来的 5xx 面板在本项目里恒为 No data，因为 HTTP 状态一律是 200。

## 4. 回归结论

- 后端 `mvn clean install` 30/30；Flyway 往返 6/6（order 为 V1→V4）。
- Playwright 31/31（`--retries=0`，覆盖浏览、交易全链路、移动端、秒杀可靠性）。
- `mobile-audit` 共 30 页，横向溢出全部 0px。
- Seata TC 重启演练 ✓；冷启动 3/3 轮（就绪后 600 个请求 0 失败）；备份 + 恢复演练 ✓。
- 改动已部署到本机 compose 栈。全部 8 个服务都已重建，因为 web-starter 有改动。

## 5. 遗留与建议

- **GitHub 首跑**：`ci.yml` 是新增的，e2e.yml 也有改动，需要推送后在 Actions 上确认一次。
- **库存流水**：商品库存回补只靠订单状态 CAS 保证只执行一次，`StockService.restore` 本身不幂等。
  建议增加 `stock_ledger(order_id, sku_id, delta)` 唯一约束记账，重复回补在库存层也能被挡住，库存也能按流水对账。
- **TC 高可用**：db 模式解决了「重启丢在途事务」，但 TC 仍是单实例，重启窗口内下单会失败（返回 503）。
  生产环境建议部署 2 个以上 TC，共用 `seata` 库，通过 Nacos 注册。
- **热点 SKU**：单 SKU 高并发普通下单会在 AT 全局锁上排队（第一轮修复前的预演中出现过 286 次 `Global lock acquire failed`）。
  可以考虑 `client.rm.lock` 重试参数，或热点商品走秒杀链路。
