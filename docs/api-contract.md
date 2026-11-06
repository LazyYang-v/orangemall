# 橙选商城 API 契约（前端 ⇄ Spring Cloud 网关）

> 本文档与 `frontend/src/mock/server.ts` 的 Mock 路由**一一对应**。后端按此实现后，把
> `frontend/.env.development` 中 `VITE_USE_MOCK` 改为 `false` 即可切到真实网关，前端无需改代码。

## 1. 通用约定

| 项 | 约定 |
|---|---|
| 前缀 | 所有接口挂在网关 `/api` 下（Vite 代理到 `VITE_GATEWAY_URL`，默认 `http://localhost:8080`） |
| 鉴权 | `Authorization: Bearer <token>`；网关校验 JWT 后把 `X-User-Id` 透传给下游服务 |
| 响应体 | `Result<T> = { code: number; message: string; data: T; traceId?: string }`，**`code = 0` 为成功**；HTTP 状态码一律 200，业务错误看 `code` |
| 401 | `code = 401` 时前端清 token 并跳转 `/login?redirect=...` |
| ID | 订单号、支付流水号、用户 ID 等雪花 ID **一律以字符串传输**（避免 JS 超过 2^53 丢精度）；类目 ID 为 number |
| 金额 | **一律整数「分」**（`19900` = ¥199.00）；折扣券 `amount = 85` 表示 8.5 折 |
| 时间 | ISO-8601 字符串（`2026-11-06T10:00:00.000Z`） |
| 分页 | 请求 `page`(从 1 开始) / `size`；响应 `PageResult<T> = { records, total, page, size }` |
| 实时推送 | WebSocket `/ws/notify?token=<token>`，消息体见 `frontend/src/types/notify.ts`（`LOGISTICS_UPDATE` / `ORDER_STATUS` / `SECKILL_RESULT`） |

完整 TS 类型定义：`frontend/src/types/domain.ts`（可直接对照生成 Java DTO）。

鉴权级别：`none` 公开 · `optional` 登录与否均可（登录后返回个性化字段）· `user` 需登录 · `admin` 需 `role = ADMIN`。

## 2. 用户服务 user-service

| 方法 | 路径 | 鉴权 | 请求 | 响应 `data` |
|---|---|---|---|---|
| POST | `/auth/login` | none | `{ phone, password? , smsCode? }`（二选一） | `{ token, user: UserProfile }` |
| POST | `/auth/register` | none | `{ phone, password, nickname?, smsCode }` | `{ token, user }`（注册送 200 积分 + 新人券） |
| POST | `/auth/sms` | none | `{ phone }` | `{ sent, hint? }`（同手机号 60s 限频） |
| POST | `/auth/logout` | optional | — | `true`（服务端把 token 加入黑名单） |
| GET | `/users/me` | user | — | `UserProfile`（含 `points`、`creditScore`、`level`、`role`） |
| GET | `/addresses` | user | — | `Address[]` |
| POST | `/addresses` | user | `Partial<Address>`（有 `id` 为更新；`isDefault=true` 时其余置 false） | `Address` |
| DELETE | `/addresses/:id` | user | — | `true` |

## 3. 商品服务 product-service

| 方法 | 路径 | 鉴权 | 请求 | 响应 `data` |
|---|---|---|---|---|
| GET | `/home` | optional | — | `HomeData`（热词、公告、秒杀预览、券预览、新品、Banner） |
| GET | `/home/recommend` | optional | `page, size` | `PageResult<SpuCard>` |
| GET | `/categories/tree` | none | — | `Category[]`（三级树，邻接表组装；建议 Caffeine + Redis 二级缓存） |
| GET | `/products` | none | `SearchQuery`：`keyword, categoryId, brandId, minPrice, maxPrice, sort(default/sales/price_asc/price_desc/new), isNew, page, size` | `PageResult<SpuCard> & { brands: Brand[] }`（后续可换 ES） |
| GET | `/products/:id` | optional | — | `Spu & { favorited }`（Cache Aside：`product:detail:{id}`，空值缓存防穿透） |
| POST | `/products/:id/favorite` | user | — | `boolean`（切换后的收藏状态） |
| GET | `/favorites` | user | — | `SpuCard[]` |

## 4. 购物车 cart-service（Redis Hash `cart:{userId}`）

| 方法 | 路径 | 鉴权 | 请求 | 响应 `data` |
|---|---|---|---|---|
| GET | `/cart` | user | — | `CartItem[]`（实时价、库存、失效状态、降价提示） |
| POST | `/cart/preview` | none | `{ items: {skuId,count,checked}[] }` | `CartItem[]`（未登录购物车存 localStorage，用此接口补全展示） |
| POST | `/cart/items` | user | `{ skuId, count }`（累加） | `number`（购物车总件数） |
| PUT | `/cart/items/:skuId` | user | `{ count?, checked? }` | `true` |
| PUT | `/cart/check-all` | user | `{ checked }` | `true` |
| DELETE | `/cart/items` | user | body `{ skuIds: string[] }` | `true` |
| POST | `/cart/merge` | user | `{ items }`（登录后合并游客购物车） | `number` |
| POST | `/cart/check` | none | `{ items: {skuId,count}[] }` | `CartCheckIssue[]`（下架/无货/库存不足/价格变化；空数组=通过） |

## 5. 订单服务 order-service

| 方法 | 路径 | 鉴权 | 请求 | 响应 `data` |
|---|---|---|---|---|
| POST | `/orders/preview` | user | `{ items: {skuId,count}[], couponId?, pointsUsed? }` | `OrderPreview & { maxPoints, selectedCouponId? }`：价格明细、可用/不可用券、`submitToken` |
| POST | `/orders` | user | `SubmitOrderReq = { items, addressId, couponId?, pointsUsed, remark?, submitToken, fromCart }` | `{ orderId, payAmount, payDeadline }` |
| GET | `/orders` | user | `status?, keyword?, page, size` | `PageResult<Order>` |
| GET | `/orders/counts` | user | — | `{ PENDING_PAYMENT, PAID, SHIPPED, COMPLETED }` |
| GET | `/orders/:id` | user | — | `Order`（含 `logistics.traces`） |
| POST | `/orders/:id/cancel` | user | — | `true`（回滚库存、优惠券、积分） |
| POST | `/orders/:id/confirm` | user | — | `{ earnedPoints }`（实付 1 元 = 1 积分） |

**计价规则**（`OrderPriceDetail`，单位分）：

- `goodsAmount`：商品总额。
- `fullReduction`：跨店满减，每满 300 减 50，最多 5 次。
- `couponDeduct`：优惠券抵扣，受券的类目范围限制。
- `pointsDeduct`：积分抵扣，100 积分 = 1 元，最多抵扣 20%。
- `freight`：运费，满 99 包邮，否则 10 元。
- `payAmount`：应付金额，最低 1 分。

**下单要点：**

- `submitToken` 一次性使用，防止重复提交，重复提交返回 `5001`。
- 锁库存采用 `UPDATE ... SET stock = stock - n WHERE stock >= n`。
- 普通单投递 30 分钟的延迟消息（RabbitMQ TTL + DLX），到期未支付则关单回滚；秒杀单为 10 分钟。

## 6. 支付服务 pay-service

| 方法 | 路径 | 鉴权 | 请求 | 响应 `data` |
|---|---|---|---|---|
| POST | `/payments` | user | `{ orderId, channel: 'ALIPAY'|'WECHAT' }` | `PayOrderVO`（同一订单重复创建返回同一流水） |
| GET | `/payments/:paymentNo` | user | — | `{ status, orderId, callbackCount }`（前端轮询） |
| POST | `/payments/:paymentNo/sandbox-pay` | user | `{ success }` | `SandboxNotify`（**仅沙箱**：模拟支付平台生成带签名的回调报文） |
| POST | `/payments/notify` | none | `{ paymentNo, amount, tradeStatus, sign }` | `{ result: 'success', idempotent?, callbackCount? }` |

回调处理要求：

1. 先验签，不通过返回 `6003`。
2. 校验金额，不一致返回 `6004`。
3. 幂等：`UPDATE pay SET status='SUCCESS' WHERE payment_no=? AND status='PENDING'`。影响行数为 0 说明已处理过，直接返回 success，并标记 `idempotent: true`。
4. 支付成功后发 MQ 消息，由订单服务把订单改为 `PAID`。

### 6.1 售后 / 退款（Phase 2，设计见 `docs/refund-design.md`）

| 方法 | 路径 | 鉴权 | 请求 | 响应 `data` |
|---|---|---|---|---|
| POST | `/refunds` | user | `{ orderId, type: 'REFUND'|'RETURN'|'EXCHANGE', reason }` | `RefundOrder`（全额；订单 → `REFUND_APPLYING`） |
| GET | `/refunds` | user | `page?, size?` | `RefundOrder[]`（本人） |
| GET | `/refunds/:refundId` | user | — | `RefundOrder` |
| POST | `/refunds/:refundId/cancel` | user | — | `true`（仅 `WAIT_APPROVAL`；订单回退到 `prevStatus`） |
| GET | `/admin/refunds` | admin | `status?, keyword?, page, size` | `PageResult<RefundOrder>` |
| POST | `/admin/refunds/:refundId/approve` | admin | `{ pass, remark? }` | `RefundOrder`；通过 → `APPROVED → REFUNDING`，渠道受理后异步回调 |

状态机：`WAIT_APPROVAL → APPROVED → REFUNDING → DONE`，旁路 `REJECTED` / `CANCELLED`（订单按 `prev_status` 回退）。
退款回调复用 `POST /payments/notify`，报文带 `refundId` 且 `tradeStatus = 'REFUND_SUCCESS'`，
签名 `HMAC(paymentNo|refundId|amount|REFUND_SUCCESS)`；以 `refundId` 为幂等键、CAS 更新，
完成后：订单 → `REFUNDED`，`payment.refunded_amount += amount`，非换货回补库存，WebSocket 推送 `REFUND_UPDATE`。
`REFUNDING` 超 24h 由 `PayJobs.alertStuckRefunds` 告警。

## 7. 秒杀服务 seckill-service

| 方法 | 路径 | 鉴权 | 请求 | 响应 `data` |
|---|---|---|---|---|
| GET | `/seckill/sessions` | optional | — | `Array<SeckillSession & { items: SeckillItem[] }>`（每 2 小时一场） |
| GET | `/seckill/items/:activityId` | optional | — | `SeckillItem & { bought, serverTime }`（倒计时以 `serverTime` 校准） |
| GET | `/seckill/:activityId/path` | user | — | `{ path }`（动态地址，60s 有效，存 Redis） |
| POST | `/seckill/:activityId/:path/orders` | user | `{ addressId? }` | `SeckillResult = { state: 'QUEUING' }` |
| GET | `/seckill/:activityId/result` | user | — | `SeckillResult`：`QUEUING` / `SUCCESS(orderId)` / `FAILED(reason)` |

**四层防护**（顺序即 `POST .../orders` 的处理顺序）：

1. **令牌桶限流（网关 / Sentinel）**：按用户限流，容量 5，速率 2/s，超出返回 `4290`。
2. **动态 path 校验 + 风控**：path 无效或已过期返回 `7003`；信用分低于 60 或在黑名单中返回 `7009`。
3. **Redis Lua 原子脚本**：`SISMEMBER seckill:bought:{id}` 判重，重复购买返回 `7004`；`DECR seckill:stock:{id}`，结果小于 0 时回补并返回 `7005`；最后 `SADD`。
4. **Kafka 异步下单**：消费端按 `UPDATE ... WHERE stock > 0` 乐观锁落库，失败则重试或进入死信。成功后通过 WebSocket 推送 `SECKILL_RESULT`。

## 8. 营销服务 marketing-service

| 方法 | 路径 | 鉴权 | 请求 | 响应 `data` |
|---|---|---|---|---|
| GET | `/coupons/templates` | optional | — | `CouponTemplate[]`（登录后带 `receivedByMe`） |
| POST | `/coupons/templates/:id/receive` | user | — | `UserCoupon`（Lua：剩余量 > 0 且 用户已领 < 限领） |
| GET | `/coupons/mine` | user | `status?` | `UserCoupon[]`（`UNUSED/LOCKED/USED/EXPIRED`） |
| GET | `/points` | user | — | `{ balance, records: PointsRecord[] }` |
| GET | `/points/goods` | none | — | `PointsGoods[]` |
| POST | `/points/goods/:id/exchange` | user | — | `{ balance }` |

## 9. 运营后台 admin（均需 `role = ADMIN`）

| 方法 | 路径 | 请求 | 响应 `data` / 说明 |
|---|---|---|---|
| GET | `/admin/dashboard` | — | `DashboardStats`（今日 GMV/订单/转化率/待发货、状态分布、近 7 日） |
| GET | `/admin/orders` | `status?, source?(NORMAL/SECKILL), keyword?(收货人/手机号, 走冗余列 receiver/phone), page, size` | `PageResult<Order>`；状态新增 `REFUND_APPLYING` / `REFUNDED` |
| POST | `/admin/orders/:id/ship` | `{ company, trackingNo? }` | `true`；推送 `ORDER_STATUS` + `LOGISTICS_UPDATE` |
| POST | `/admin/orders/:id/expire` | — | **仅 Mock/测试环境**：让延迟消息立即到期 |
| GET | `/admin/products` | `keyword?, status?` | `Spu[]` |
| PUT | `/admin/products/:id/status` | `{ status: 'ON_SALE'|'OFF_SALE' }` | `true`（延迟双删商品缓存） |
| PUT | `/admin/skus/:id` | `{ price?, stock? }` | `true`（先写 DB → DEL 缓存 → 500ms 后再 DEL） |
| GET | `/admin/categories` | — | `Category[]`（树） |
| POST | `/admin/categories` | `{ parentId, name }` | `Category` |
| PUT | `/admin/categories/:id` | `{ name }` | `true` |
| DELETE | `/admin/categories/:id` | — | `true`；有子类目 `9004`、有商品 `9005` |
| GET | `/admin/coupons` | — | `CouponTemplate[]` |
| POST | `/admin/coupons` | `Partial<CouponTemplate>` | `CouponTemplate` |
| GET | `/admin/seckill` | — | `{ sessions: (SeckillSession & { items: AdminSeckillItem[] })[], metrics: SeckillMetrics }` |
| POST | `/admin/seckill/:activityId/warmup` | — | `true`（`SET seckill:stock:{id}`、清空已购集合） |
| POST | `/admin/seckill/:activityId/simulate` | `{ users }` | **仅压测环境**：`{ requests, success, soldOut, limited, redisLeft, dbLeft }` |
| GET | `/admin/cache/check` | — | `{ rows: CacheCheckRow[], hitRate, hit, miss }` |
| POST | `/admin/cache/repair` | `{ key }` | `true`（以 DB 为准回写 Redis） |
| GET | `/admin/seckill/health` | — | 秒杀可靠性（Phase 3 OM-013/019）：`{ consumer:{running,paused}, lag, stuckSeconds, autoReset, inflight:[{activityId,session,inflight,heldSeconds,state}], deadLetters:{new,total} }`；`lag=-1` 表示积压探测失败；`state` ∈ NEW 观察中 / BACKLOG 消费积压 / UNKNOWN 探测失败 / LEAK 计数泄漏 |
| GET | `/admin/seckill/dead-letters?status=NEW\|RESOLVED` | — | 最近 100 条死信：`{ id, activityId, userId, reason(POISON/INVALID/EXHAUSTED/COMPENSATE_FAILED), error, payload, srcPartition, srcOffset, status, remark, resolvedAt, createdAt }[]` |
| POST | `/admin/seckill/dead-letters/:id/resolve` | `{ remark? }` | `true`；已处理或不存在 → `7011` |
| POST | `/admin/seckill/:activityId/inflight/reset` | `{ force? }` | 扣除当前滞留在途并以 DB 为准回补 Redis 库存：`{ reset, cleared, inflight, redisStock, dbStock }`；主题仍有积压或探测失败且未 `force` → `7010` |
| POST | `/admin/seckill/consumer/pause` · `/resume` | — | **故障演练**：暂停 / 恢复秒杀主消费者 `{ paused }`；未就绪 → `7012` |
| GET | `/admin/users` | `keyword?(手机号/昵称), page, size` | `PageResult<AdminUser>`（Phase 2） |
| GET | `/admin/users/:id` | — | `AdminUser`；不存在 `9006` |
| GET | `/admin/risk` | — | `RiskUser[]`（按信用分升序） |
| PUT | `/admin/risk/:userId` | `{ blocked }` | `true` |
| GET | `/admin/logs` | `type?` | `OpsLog[]`（最多 100 条；真实环境可对接 SkyWalking / ELK） |
| POST | `/admin/mock/reset` | — | **仅 Mock** |

## 10. 业务错误码

| 段 | 码 | 含义 |
|---|---|---|
| 通用 | 401 / 403 / 404 | 未登录或过期 / 无权限 / 接口不存在 |
| 1xxx 用户 | 1001 账号不存在 · 1002 密码或验证码错误 · 1003 手机号格式错误 · 1004 已注册 | |
| 2xxx 商品 | 2001 商品不存在或已下架 | |
| 3xxx 购物车 | 3001 库存不足 · 3002 单品最多 99 件 · 3003 购物车无此商品 | |
| 4xxx 营销 | 4001 券不存在 · 4002 活动结束 · 4003 券已抢光 · 4004 达到限领 · 4005 券不可用 · **4290 限流** | |
| 5xxx 订单 | 5001 重复提交 · 5002 未选地址 · 5004 订单不存在 · 5005 不可取消 · 5006 不可确认收货 · 5009 风控拦截 | |
| 6xxx 支付 | 6001 订单不可支付/已关闭 · 6002 流水不存在 · 6003 验签失败 · 6004 金额不一致 | |
| 61xx 售后 | 6101 当前订单状态不可售后 · 6102 售后单不存在 · 6103 已有进行中的售后单 · 6104 售后单已受理/与原流水不匹配 | |
| 7xxx 秒杀 | 7001 活动不存在 · 7002 未开始/已结束 · 7003 path 无效 · 7004 已抢过 · 7005 已抢完 · 7009 风控拦截 · 7010 在途重置被拒（有积压，需 force）· 7011 死信不存在/已处理 · 7012 消费者未就绪 | |
| 8xxx 积分 | 8001 商品不存在 · 8002 已兑完 · 8003 积分不足 | |
| 9xxx 后台 | 9001 仅待发货可发货 · 9002 仅待支付可模拟超时 · 9003 类目不存在 · 9004 有子类目 · 9005 类目下有商品 · 9006 用户不存在 | |

## 11. 后端实现状态（2026-11-06）

以上契约已全部由 Spring Cloud 微服务实现并通过验证，详见 `docs/backend-design.md`。
要点与少量偏差：

| 项 | 状态 |
|---|---|
| 全部前端接口（网关 `/api/**`） | ✅ 已实现，行为与 Mock 一致（HTTP 恒 200 + `Result`，雪花 ID 字符串，金额整数分） |
| `/ws/notify` WebSocket | ✅ 真连接（`ws://localhost:18080/ws/notify?token=…`），消息体含 `LOGISTICS_UPDATE` / `ORDER_STATUS` / `SECKILL_RESULT` |
| `/admin/mock/reset` | ❌ 真实后端不实现（仅 Mock 重置用），真实环境调用返回「接口不存在」 |
| `/auth/sms` | ✅ 假发送 + 60s 限频，验证码固定 `123456` |
| 支付回调 | 沙箱 HMAC 签名异步回调（真实 RSA2 证书不可得，链路结构一致） |
| 配送订阅（快递鸟） | 用 12s/步的定时物流模拟器替代，事件推送结构（trace 数组/状态码）与 Mock 相同 |
| 物流单号 | 留空自动生成 `SF` 前缀 + 雪花号段 |
| 秒杀 | 四层防护（令牌桶/path+风控/Lua 判重扣减/Kafka 异步落库）；DB 乐观锁兜底 + 一致性巡检 |
| 订单分片 | 真实 4 分片（user_id & 3），管理端 UNION ALL 聚合；Mock 下前端无感知 |
