# Phase 1 实施报告（交易链路与安全修复）

> 依据 `docs/AUDIT-2026-11-06.md` 的分阶段计划（OM-001/002/003/004/005/007/010）与
> `docs/refund-design.md`（OM-008 本期仅设计）。实施时间 2026-11-06。全部改动已编译、部署并回归验证。

## 1. 交付内容（逐项对应审计编号）

| 编号 | 内容 | 关键改动 | 验证证据 |
|---|---|---|---|
| OM-001 | 决策 D3 落地：seed 密码下线 | 账号创建改为管理侧/首次注册流程，不再写明文公共密码 | 全库 `grep` 无公共密码明文 |
| OM-002 | 支付越权（A 可为 B 的订单创建支付） | `PayService.create` 增加归属断言 `findOwned`，非本人单据 → `6002 支付流水不存在`；`status`/`sandboxPay` 同步按 `(userId, paymentNo)` 签名 | 回归 ②：A 对 B 订单 `POST /api/payments` → `6002` |
| OM-003 | 支付↔订单对账缺失 | mall-pay 新增 `GET /inner/pay/paid?from=` （`InnerPayController`）；mall-order 新增 `ReconcileJob`（5 分钟，Redis SETNX 互斥 + 检查点 `order:reconcile:since`，最多回补 24h）；实证链路打开 | 人为把已收款订单回拨 PENDING_PAYMENT → 下一轮 `[PAY] [对账] 订单…触发补偿标记` → 订单恢复 PAID |
| OM-004 | 订单关单 MQ 丢失无自愈 | `order.close.queue`/`order.pay.success.queue` 挂 TTL+DLX；新增 `close.dlx`/`close.dlq`、`pay.notify.dlx`/`pay.notify.dlq` 与 `DlqListeners`（重放关单/支付成功） | `rabbitmqctl list_queues` 出现 `order.close.dlq`、`pay.notify.dlq`；`pay.notify_log` 补 `idx_created` 索引 |
| OM-005 | 支付回调可伪造/无状态页鉴权 | `notify` 维持原签名 → `6003 验签失败` CAS 链；新增 `status`/`sandboxPay` 必须登录（`UserContext.requireUser`） | 回归 ➀：篡改 sign → `6003`；6 正确签名 → 订单 PAID |
| OM-007 | path 发放无频控 | 应用层 `INCR+EXPIRE(key,60s)`（用户,活动）每分钟 5 次 → `4290 当前抢购人数过多`；Nginx ip 级 `30r/s burst=60` | 回归 ④：1–5 次 code=0，第 6/7 次 `4290`；计数自动过期 |
| OM-010 | 内部接口裸奔 | `mall-web-starter` 新增 `InnerKeyFilter`：`/inner/**` 必须带 `X-Inner-Key`（嵌套占位符 `${mall.inner-key:${INNER_SECRET:dev-inner-key}}`）；`mallFeignInterceptor` 统一注入头；compose 强制 `${INNER_SECRET:?}` | 回归 ③：无 key/错 key → `401 内部接口未授权`；正 key → `code=0` 积分 -2 落库；网关层 `/inner/**` → 404 不可路由 |
| OM-008 | 退款/售后域 | 仅设计：`docs/refund-design.md`（DDL/状态机/接口/联动/DoD），实施于 Phase 2 | — |

密钥治理：`backend/deploy/.env.example` 模板 + `.env`（真实随机值，已 `.gitignore`），
`JWT_SECRET/PAY_SANDBOX_KEY/INNER_SECRET` 在 compose 中全部改为 `${VAR:?}` 硬失败。

## 2. 过程中发现并修复的两个真实缺陷（审计未列）

1. **模拟物流器无界增长**（`Jobs.advanceLogistics`）：到达最后一步后仍每 12s `prepend` 复制「已签收」，TEXT(64KB) 用 ~100 分钟被写爆；且单条订单异常会中断整个_pass_。
   修复：最新轨迹等于终态即停止追加 + 订单级 try/catch 隔离；被污染订单数据复原（65418 → 1010 字节）。
2. **-fat jar 不重写**：`mvn package` 未 clean 时 spring-boot repackage 不会覆盖已生成的 fat jar，导致 5 个服务镜像"打新包镜像旧"。修复：清理后 clean+package 重打 mall-user/product/marketing/cart/gateway，并验证内嵌 starter 已含 InnerKeyFilter（16079 字节 = 新包）。

## 3. 回归结论

- 安全回归 ①②③④⑥ 全部 PASS（脚本断言 + 服务/DB/Redis 证据），⑤ DLQ 队列存在、ReconcileJob 补偿实证 PASS。
- 全链浏览器 E2E（`/tmp/om-e2e/flow-backend.mjs`，16 页面含 admin 全菜单）：支付回调落库、发货、秒杀 1000 并发无超卖、缓存一致性校验全绿、页面错误 0。

## 4. 部署口径

`backend/deploy`：`cp .env.example .env`（openssl rand 生成三个密钥）→ `docker compose up -d` → `docker compose --profile app build && docker compose --profile app up -d`。
宿主机 8080/8081/8083/16379 属于用户其它程序，未占用；网关入口 `18080` / Nginx `8090`。
