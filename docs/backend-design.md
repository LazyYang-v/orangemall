# 橙选商城后端设计文档（Spring Cloud 微服务）

> 以 `docs/api-contract.md`（前端 ⇄ 网关契约）+ `frontend/src/mock/server.ts`（Mock 行为细节）为唯一规范，
> 实现一套可独立部署、可通过压测验证的 B2C 电商后端。本文档描述架构、关键机制、验证结果与已知差异。

## 1. 总体架构

```
浏览器
   │  http://localhost:8090  Nginx —— 静态资源 SPA · /api 反代网关 · /ws WebSocket 升级 · 秒杀限流区
   ▼
mall-gateway :18080（Spring Cloud Gateway）
   │  · JWT 校验（Authorization: Bearer / ?token=），/auth/logout 后的 token 进 Redis 黑名单
   │  · /api/admin/** 仅放行 ADMIN（401/403，业务 JSON 响应体）
   │  · 注入 X-User-Id / X-User-Role / X-Trace-Id  →  下游服务 StripPrefix=1 转发
   │  · 404/500/503 统一包装为 Result{code,message,data,traceId}（HTTP 恒 200）
   │  · WebSocket: ws://…18080/ws/notify → ws://mall-order/ws/notify（鉴权走 AuthInterceptor）
   ▼
服务（Nacos 注册，服务间 Feign + Seata AT）
  user      :9001  注册/登录/地址/积分/收藏/风控命中/链路日志/admin 数据面板
  product   :9002  首页/商品/类目/评价；详情 Cache Aside（hit/miss 统计入 cache:stats）
  cart      :9003  购物车（纯 Redis Hash，field=skuId，无 DB）
  marketing :9004  券模板/人券领取使用/积分兑换/秒杀预览
  order     :9005  预览/下单/取消/收货/发货；分片 4 库；TTL 关单/物流模拟/归档/看板
  pay       :9006  沙箱收单 + 异步回调（HMAC 验签/CAS/MQ/幂等/审计）
  seckill   :9007  四层防护 + Kafka 异步落库 + 提交前预热 + 一致性巡检
   │
   └─ 基础设施（docker compose）：MySQL 8 · Redis 7 · RabbitMQ 3.13（TTL+DLX）· Kafka（KRaft，3 分区）
      Nacos v2.3.2（注册中心）· Seata 2.0.0（AT，file 模式）· Nginx 1.27
```

共享模块（install 到本地 ~/.m2，被各服务依赖）：
- `mall-common`：`Result` / `PageResult` / `BizException` / 常量 / 雪花 `IdGenerator`（userId 做 gene）/ `JwtUtil`。
- `mall-api`：跨服务 Feign 客户端与 DTO（`/inner/**` 内部接口）。
- `mall-web-starter`：`UserContext` / `ContextFilter` / `GlobalExceptionHandler` / `OpsLogger`（落表 ops_log）/
  `Notifier`（Redis PubSub → WebSocket）/ MyBatis-Plus 自动配置。

## 2. 网关

- `AuthGlobalFilter`：无 token 的需要登录接口返回 `{code:401}`；ADMIN 校验 `/api/admin/**`；
  白名单（登录/注册/短信/公共读）无需 token；响应回填 `X-Trace-Id`。
- 统一错误处理：503「服务繁忙」/ 404「接口不存在」也返回 JSON `Result`，前端无需判 HTTP 状态。
- 路由：按服务前缀谓词逐一声明（/api/auth/** /api/products/** /api/orders/** ...），`StripPrefix=1`；
  秒杀订单提交路径单独声明限流（`limit_req_zone` 200r/s burst 400 由 Nginx 完成）。

## 3. 各服务要点

### 3.1 order（最复杂：分片 + 事务 + 异步关单）
- **分片**：`t_order_{0..3}` / `t_order_item_{0..3}` / `_archive`，分片键 `user_id & 3`；
  用户侧读写按 `Shard.of(userId)` 定位；管理端 `Shard.ALL` UNION ALL 聚合 + 全局排序分页。
- **下单** `@GlobalTransactional`：submitToken 防重（Redis SETNX 15min）→ 风险预检（creditScore<300 或
  风控黑名单拒绝 5009）→ 校验地址/券（UNUSED 4005）/库存 → product.deduct → marketing.lock →
  user.changePoints（负扣）→ 落库（雪花 id，items 逐单插）→ 购物车剔除（best-effort）→
  发 30s/10s TTL 消息。任一步失败，Seata 回滚还原库存/券/积分（已实测）。
- **TTL 关单**：RabbitMQ `order.delay.exchange` → `order.delay.10m/30m`（TTL 队列）→ DLX
  `order.close.exchange` → `order.close.queue` 监听关闭：CAS 校验 PENDING → 库存回滚（仅在场商品）→
  券释放 → 积分回退 → ops 日志 + WebSocket。
- **物流模拟**：发货后定时器 12s/步推进（揽收→运输→派送→签收），每步 ops.log + WebSocket
  `LOGISTICS_UPDATE` 推送（快递鸟真实订阅的替代实现，见 §4）。
- **归档**：每天 03:30（Asia/Shanghai）归档 90 天前订单到 `_archive` 表。
- **看板**：状态分布 + 近 7 天销量（时间戳转上海日期字符串，跨 4 片聚合）。

### 3.2 seckill
- **四层防护**：
  1. `guard.lua`：per-user 令牌桶（容量 5 / 2 rps）→ 4290 限流；path 校验（UUID，60s TTL）→ 7003；
     风控（risk:credit / risk:blocked）→ 7009。
  2. `stock.lua`：bought SET 单人单购 → 7005 重复购买；DECR Redis 库存原子扣减（<0 回补）→ 7005 售罄；
     成功则 SADD bought + INCR inflight。
  3. Kafka `seckill-order`（3 分区，key=activityId）削峰；消费端 `SeckillOrderProcessor` 内部重试
     （1s→2s→4s→8s×2 ≈ 23s），耗尽后补偿（回补 Redis/DB、置 FAILED 并留档）。
  4. DB 乐观锁兜底：`UPDATE … WHERE stock>0` + `seckill_order` 唯一键幂等 + Feign `order.createSeckill`
     生成真实订单（幂等 orderId）。模拟请求（sim=true，admin 触发）只扣库存统计、不真实建单。
- **一致性巡检**：每 60s 对比 Redis 剩余 = DB 剩余 − inflight，在途归零且不一致时以 DB 为准自动修复；
  admin /admin/cache 展示逐 key 校验结果。
- **滚动建场**：每 30s 以 `k=-1..2` 窗口 INSERT IGNORE 生成 8 商品/场次（2 小时一场，
  Asia/Shanghai，预热比例 weekday/weekend 不同），历史场次 sold 比例暖暖预热。

### 3.3 pay
- `payment_no = "P" + snowflake`；回调签名 HMAC-SHA256（paymentNo|amount|tradeStatus，沙箱密钥可配）；
  验签失败 6003 / 幂等（已成功支付重复回调 `idempotent:true`）/ 金额不符 6004 / 关闭订单→退款分支。
- 成功回调 CAS `UPDATE payments SET status='TRADE_SUCCESS' WHERE status='PENDING'`（防并发）→ 发
  MQ `pay.exchange → order.pay.success.queue`（Jackson2Json）→ order 侧标记 PAID + ops + WebSocket。

### 3.4 user / product / marketing / cart
- **user**：注册送 200 积分与新人券；`/auth/sms` 60s 同号限频（验证码 mock 值 123456）；
  风控命中数据 + 链路日志 admin 展示；`/admin/mock/reset` 仅供 mock 环境使用（真实后端不提供重置）。
- **product**：详情 Cache Aside（Caffeine 30s + Redis 5min，DB 兜底；命中率读 `cache:stats`）；
  首页接口用下游 Feign 聚合（秒杀预览、券预览，延迟加载失败容忍降级）。
- **marketing**：券模板/和人券领取（每人每种限 1）；使用状态机 UNUSED→LOCKED→USED；积分兑换。
- **cart**：纯 Redis Hash `cart:{userId}`，合并数量封顶 99、库存校验，置 SKU 无法购买时不阻塞只做
  状态.showcase（下架/无货）。

## 4. 与 Mock / 真实生产间的差异（契约兼容性）

| 领域 | Mock | 后端 | 原因 |
|---|---|---|---|
| 短信 | 假发送 | 假发送 + 60s 限频（验证码固定 123456） | 真实短信通道不可得 |
| 支付 | 函数内回调 | HMAC 沙箱 + 异步回调链路（同真实支付宝） | 真实支付证书不可得；保留全链路结构 |
| 物流 | Mock 状态机 | 12s/步定时器 + MQTT 事件推送（模拟快递鸟） | 快递鸟订阅需实名/鉴权，替代实现保留事件接口 |
| 导航推送 | 函数内回调 | Redis PubSub → WebSocket | 同多实例真实推送结构 |
| 库存 | 直接调用 | Redis 预热 + DB CAS | 秒杀峰值保护 |
| 分布式事务 | 不涉及 | Seata AT @GlobalTransactional | 真实跨服务一致性 |
| 校验规则 | 前端守卫 | 服务端 submitToken / 幂等回调 / 黑名单 | 防刷与安全 |
| 运行时 | 前端 Mock | 全部真部署（含限流） | 验收即试运行 |

其余接口行为、错误码、字段含义与 Mock 一一对应（`docs/api-contract.md` 有逐条列出）。

## 5. 部署与运行

- **基础设施**：`cd backend/deploy && docker compose up -d`（不加 profile，只起 mysql/redis/rabbit/kafka/nacos/seata）。
- **全套容器**：先在 `backend/` 下 `mvn -q -f mall-xxx/pom.xml package -DskipTests`（7 个业务 Jar）+
  前端 `npm run build`，然后 `docker compose --profile app up -d --build`。
  入口：Nginx http://localhost:8090 · 网关 http://localhost:18080 · 只作内部通信的服务不对外暴露。
- **前端开发模式**：`frontend/` `npm run dev`（Mock）/ `npm run dev:backend`（真后端，代理 /api → 18080）。
- **验收账号**：普通用户 `13800000000 / 123456`（验证码任意短信场景填 123456）；
  运营后台 `18800000000 / admin123`。

## 6. 验证与压测结果（Mac Docker + 本机 JDK，服务已容器化运行）

| 项 | 场景 | 结果 |
|---|---|---|
| GET /api/products/1006（缓存命中） | 网关 c=200 × 20000 | ≈1550 QPS，0 业务失败（抽样 1000 响应 100% code=0） |
| GET /api/seckill/sessions | 网关 c=200 × 10000 | ≈530 QPS（批量 SQL 优化前 127，3.8× 提升） |
| POST /seckill/:aid/:path/orders（守护层拒绝路径） | 直连 c=200 × 20000 | ≈1610 QPS（guard.lua 限流/soldout 拦截，指标同步 +20000） |
| POST /payments/notify（幂等回调） | 网关 c=100 × 8000 | ≈230 QPS；直连 c=200 ≈400 QPS |
| 秒杀模拟 1000 并发（后台触发） | 一场库存 50 | 请求 1000 → 售罄 1000、orders 50 = 库存、无超卖、Redis/DB 一致 |
| Seata 全局回滚 | 强制 marketing.lock 失败 | product.deduct 已扣库存原样恢复（87→87），coordinator 回滚成功 |
| TTL 关单 | 下单不支付 | 30 分钟后（或 admin 手动触发）订单自动关闭，库存/券/积分全回滚 |
| WS 物流推送 | 发货后 | 12s/步推进，trace 1→4 条，状态 DELIVERING → SIGNED 对 E2E 装填 |
| 幂等 | 重复回调 | 第一笔 `idempotent:false`，后续全部 `idempotent:true`（callbackCount 累增） |

> ab 对 chunked 响应的「Length failed」为分帧误报，实际以上述独立客户端逐请求校验为准（1000/1000 通过）。

## 7. 浏览器 E2E 验收（真实后端，16 组截图）

登录 → 首页 → 商品详情 → 购物车 → 结算 → 提交订单 → 沙箱支付（回调落库）→ 订单列表 → 个人中心 →
券/积分/秒杀/品牌/排行/搜索/404页 → 运营后台全部页 → 管理端发货 → 秒杀模拟（无超卖）→ 缓存一致性 →
用户确认收货（积分 2680 → 10429）→ 用户秒杀详情页。**全流程零 page error / console error**。

结论：后端微服务全部建成并验证——功能与 Mock 保持一致，并补齐了 Mock 无法演示的
「真实异步关单 / TTL 队列 / Kafka 削峰 / 分布式事务 / 幂等回调 / Redis 一致性巡检」六类生产机制。
