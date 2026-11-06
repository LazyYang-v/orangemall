# Phase 3 实施报告（秒杀专项：滞留告警 · 死信 · 压测基线）

> 依据 `docs/AUDIT-2026-11-06.md` Phase 3：「path 频控、滞留告警、DLT handler、k6 入库 + 基线重测；
> DoD：5000 用户模拟无超卖、告警可测、基线更新」，目标「sessions ≥ 1500、submit 拒绝路径 ≥ 3000、有效下单链 200 RPS」。
> path 频控（OM-007）已在 Phase 1 完成。实施时间 2026-11-06，改动均已部署到本机 compose 栈并回归。

## DoD 结论

| DoD | 结果 | 证据 |
|---|---|---|
| 5000 用户模拟无超卖 | ✓ 1000 成 / 4000 罄 / 0 异常；库存消耗 = seckill_order = 订单 = 已购集合 = 1000，Redis = DB = 0，无一人多单 | `bench/verify.sh`，`bench/results/raw/verify-e2e-run3-batcher.txt`；`prepare.sh` 修复后 8 轮 verify（纯落单 6、剖析 1、全链路 1）全 ✓ |
| 告警可测 | ✓ 4 个演练脚本均自动断言通过：投毒、在途泄漏自动重置、消费者宕机滞留/恢复、建单服务宕机补偿 | `bench/drills/*.sh`，后台「秒杀活动 → 可靠性」面板，Playwright 04 |
| 基线更新 | ✓ 四项目标全部达标 | `bench/results/2026-11-06.md` |

| 基线 | 目标 | 优化前 | 终版 |
|---|---|---|---|
| 场次列表 | ≥ 1500 QPS | 356 QPS，p95 5.69s | 1499 QPS，p95 9.7ms |
| 拒绝路径 | ≥ 3000 QPS | 2346–2819 QPS，p95 0.57–1.38s | 3003 QPS，p95 76.3ms |
| 有效落单链 | ≥ 200 单/s | 17 单/s（全链路）/ 108–156（纯落单） | 233–259 单/s（纯落单），全链路 124 单/s（受 k6 同机登录限制） |

## 1. 交付内容

| 编号 | 内容 | 关键改动 | 验证 |
|---|---|---|---|
| OM-013 | 在途滞留告警 + 强制重置 | `SeckillReliabilityService.watchInflight`：Redis Hash `seckill:inflight:watch` 记录在途值与持续时间；不变 ≥ 180s 告警，按 Kafka 积压分流：有积压（BACKLOG）只告警、无积压（LEAK）`inflight_reset.lua` 自动重置、探测失败（UNKNOWN）只告警；回落 0 发「在途恢复」告警。`SeckillJob` 每轮懒探测一次积压；一致性修复放宽到已结束场次 | `drills/inflight-leak.sh`、`drills/consumer-down.sh` |
| OM-013 后台 | 运维接口 + 面板 | `GET /admin/seckill/health`、`POST /admin/seckill/{aid}/inflight/reset {force}`（有积压且未 force → 7010）、`POST /admin/seckill/consumer/{pause\|resume}`（7012）；前端 `SeckillReliability.vue`：消费者状态/暂停恢复、Kafka 积压、滞留活动表（重置/强制重置）、3s 轮询 | Playwright 04（2 例） |
| OM-019 | DLT 监听 + 留档 + 告警 | 非 JSON（POISON）/ 缺字段（INVALID）/ 重试耗尽（EXHAUSTED）/ 补偿失败（COMPENSATE_FAILED）统一经 `DeadLetterPublisher` 进 `seckill-order.DLT`；`SeckillDltListener`（独立组 `mall-seckill-dlt`）落 `seckill_dead_letter` 表并发 ALERT；`GET /admin/seckill/dead-letters?status`、`POST …/{id}/resolve {remark}`（7011）；后台死信表（待处理/已处理/全部、标记处理） | `drills/poison.sh`、`drills/order-down.sh` |
| OM-025 | k6 压测 + 基线入库 | `bench/`：3 个 k6 脚本（场次读 / 拒绝路径 / 5000 用户全链路）、5000 压测用户 seed、`prepare.sh`（等静默后清理）、`verify.sh`（DB/Redis 口径不超卖核对）、`chain-throughput.sh`（纯落单吞吐）、`profile-drain.sh`（落单线程快照）、docker 版 k6 运行器 | `bench/results/2026-11-06.md` |
| OM-025 性能 | 达标所需的修复 | 见 §2 第 2–7 条 | 同上 |
| 文档 | | `api-contract.md` §9 新增 5 个接口、§10 错误码 7010/7011/7012；`backend-design.md` §3.2 更新；`bench/README.md`；根 README 链接 | — |

## 2. 过程中发现并修复的问题

1. **消费组重放事故（spring-kafka `@KafkaListener(id=…)` 陷阱）**：为支持暂停/恢复给监听器加了 `id`，
   spring-kafka 默认把 id 当作 group.id，新组以 `earliest` 从头重放了主题里 204 条历史模拟消息，积压探测（仍看 `mall-seckill`）也随之误判。
   修复：`idIsGroup = false`（代码内注释说明不可去掉）；停服务后把 `mall-seckill` 位点复位、删除误建的组。
   影响仅限 Redis 计数与指标（模拟消息不建真实订单）：100401 的 Redis 库存经 `/admin/cache/repair` 以 DB 为准修复，`optimisticFail` 指标回调 −204，DB 未受影响。
   这次事故也真实触发了 BACKLOG 分支告警（有积压只告警不重置），客观上验证了分流逻辑。
2. **场次列表慢（356 QPS）**：逐活动 `selectById` + 逐 key `GET` → 活动行本地缓存（10s）+ 库存 `MGET`。
3. **拒绝路径 Lua 全局锁**：`DefaultRedisScript.setLocation(ClassPathResource)` 每次 `getSha1()` 在锁内读 jar 资源，375 个线程 WAITING → 启动时读成字符串。
4. **业务异常 INFO 日志风暴**：每个 7003/4290 一行（≈12 万行/分钟）→ 降为 DEBUG；mall-web-starter 变更后 7 个业务服务全部重打镜像。
5. **落单单分区串行**：key=activityId 导致一场活动只用 1 个分区 → key=userId、12 分区、12 消费线程（分区数可配，只增不减）。
6. **`createSeckill` 单分支 Seata 全局事务**：只写订单库却走 `@GlobalTransactional` → 本地事务 + `DuplicateKeyException` 幂等；地址 Feign 合并为 1 次。
7. **DB 库存热点行护航（落单吞吐决定性瓶颈）**：线程快照 12 个消费线程 7 个卡在 `UPDATE … stock = stock - 1`。
   新增 `SeckillDeductBatcher` 合并扣减：同一本地事务内 `INSERT IGNORE seckill_order` → `stock = stock − k WHERE stock >= k`，不足时回滚逐条。
   **同时修复潜在缺陷**：原「扣库存」与「写成交记录」是两次自动提交，写成交失败后重试会重复扣 DB 库存；现在两者同事务、由唯一键保证重试幂等。
   效果：纯落单 151 → 233–259 单/s；全链路提交→成功 max 16.85s → 8.22s。
8. **压测流程互相污染（假超卖）**：中途失败的一轮在 EXIT trap 中恢复了消费者，其积压在下一轮清表时继续落单，核对出现「成交 1024 > 库存 1000」。
   排查确认已购集合 = 1000、无重复用户，是流程问题；`prepare.sh` 改为先等静默态（消费者运行、积压与在途归零）再清理。
9. **bash 变量紧跟全角字符**：`$r（` 在 `set -u` 下被当成变量名的一部分报 unbound → 脚本统一 `${var}`。
10. **线上 `seckill_activity` 5 个列注释乱码**：早期 ALTER 未带 utf8mb4（schema 文件本身正确）→ `ALGORITHM=INSTANT` 只改注释修复，全库扫描无其它乱码列。

## 3. 回归结论

- 压测：四项基线达标（详见 `bench/results/2026-11-06.md`）；8 轮 verify 全 ✓。
- 演练：poison ✓、inflight-leak ✓、consumer-down ✓、order-down（新增）✓。
- 售后 API 回归 20/20（服务重启后首轮在「支付成功 → 申请售后」处失败 1 次：支付成功经 MQ 异步更新订单，刚重启时抢跑；重跑通过）。
- 后端流程脚本（下单/支付/发货/确认收货/秒杀，页面错误 0）通过；Playwright 31/31 ×2（新增 04 秒杀可靠性 2 例）；mobile-audit 19 页横向溢出 0px。
- `vue-tsc` + `vite build` 通过；mall-seckill / order / user / product / cart / marketing / pay 均以 `clean package` 重打镜像部署。

## 4. 已知限制 / 未做

- **基线为同机数值**：k6 与全栈共用 8 CPU；全链路的 124 单/s 受 k6 登录与取 path 速度限制，落单能力以纯落单 233–259 单/s 为准。服务刚重启的首轮（冷 JIT）明显偏低（拒绝路径 2642 QPS / 落单 168 单/s），基线取热态。
- **OkHttp 连接池 / SKU 本地缓存**：已上线，但单独收益在噪声范围内，未单独量化；SKU 缓存 10s，后台改原价最多延迟 10s 反映到秒杀订单的原价快照（成交价不受影响）。
- **秒杀订单超时关单不回补秒杀库存**（只有普通单回补商品库存），属沿用的业务口径；如需「未支付释放名额」需另行设计（涉及已购集合与一人一单）。
- 合并器为单 JVM 内单刷写线程；多实例部署时各实例各自合批，热点行更新次数按实例数线性增加，仍远低于逐单更新。
- 死信只留档与人工标记处理，不提供一键重放（避免对已补偿的消息二次落单）。
- 运营后台 375px 仍有横滑（/admin 701px、订单 759、售后 726、秒杀 658），Phase 2 已声明为桌面工具定位，建议 Phase 4 处理。
- 全部改动尚未提交（等待确认后再 commit/push）。

## 5. Phase 4 建议

按审计计划：Flyway 迁移（含 up/down 往返）、Testcontainers 集成测试（可直接把 `bench/drills` 的断言迁成用例）、CI 门禁、
Prometheus + Grafana（把本期的在途/积压/死信/落单吞吐做成面板，OM-020）、备份恢复脚本与演练；
另建议并入：运营后台移动端适配、Seata TC 单点（OM-017）。
