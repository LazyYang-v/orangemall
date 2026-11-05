# 橙选商城前端设计说明

## 1. 目标与边界

- 按设计稿（`橙选商城双十一购物首页.png`）还原首页，并补齐交易闭环、秒杀、营销与运营后台的全部页面。
- 本阶段**只写前端**。后端行为由浏览器内 Mock 模拟，并尽量还原真实系统的约束，便于日后逐个服务替换：
  - 状态机流转；
  - 幂等处理；
  - 延迟关单；
  - 缓存一致性；
  - 秒杀四层防护。
- Mock 与真实后端共用同一份接口契约，见 `docs/api-contract.md`。

## 2. 视觉规范

| 项 | 取值 |
|---|---|
| 主色 | `#ff5a00`，渐变 `#ff8a1f → #ff5000`；价格红 `#f2270c` |
| 版心 | 1280px 居中，页面最小宽度 1320px（PC 商城） |
| 圆角 | 卡片 14px、按钮胶囊形、小元素 6px |
| 字体 | PingFang SC / 微软雅黑；数字使用 DIN 系列并开启等宽数字（`.num`） |
| 商品图 | `ProductArt` 矢量插画占位（20 种品类），离线可用、无版权问题；后端返回 `imageUrl` 后传 `src` 即可替换 |
| 图标 | `Icon.vue` 内联 SVG 线性图标，不引入图标字体 |

后台采用深色侧栏加浅灰工作区，与商城端区分。后台图表遵循以下规则：

- 每个图只表达一个度量，不做双 Y 轴；
- 单一色相表示量级；
- 柱顶 4px 圆角并贴住基线；
- 只直接标注峰值；
- 悬停时显示 tooltip；
- 提供「表格」视图。

## 3. 架构

```
View ──> Pinia Store ──> api/* ──> axios(http.ts)
                                     ├─ VITE_USE_MOCK=true  → mock/adapter → mock/server（路由 + 业务规则）→ mock/db（localStorage）
                                     └─ VITE_USE_MOCK=false → /api → Spring Cloud Gateway
WebSocket /ws/notify ──> useNotify ──（Mock 时为 mock/bus 进程内事件）
```

- **统一响应处理**（`http.ts`）：
  - 解包 `Result<T>`，`code ≠ 0` 时抛出 `ApiError` 并 toast 提示；
  - `401` 时清除登录态并跳转登录页。
- **Mock 适配器**：响应数据经过 JSON 序列化后再返回，和真实 HTTP 一样拿到的是副本。这样页面不会意外修改 Mock 数据库，Vue 也能正确感知每次刷新。
- **路由守卫**：`meta.auth` 要求登录，`meta.admin` 要求管理员；未登录时带 `redirect` 参数跳转登录页。

## 4. 关键交互与对应的后端设计

| 场景 | 前端做法 | 后端要点（Mock 已模拟） |
|---|---|---|
| 游客购物车 | 存 localStorage，登录后调用 `/cart/merge` | Redis Hash `cart:{userId}` |
| 结算前校验 | `/cart/check` 返回问题清单弹窗 | 下架、无货、库存不足、价格变化 |
| 防重复下单 | 预览接口下发一次性 `submitToken` | Redis `DEL` 返回 1 才放行 |
| 超时关单 | 收银台倒计时，到期后订单自动变为「已关闭」 | RabbitMQ TTL + DLX，普通单 30 分钟、秒杀单 10 分钟，关单时回滚库存、券和积分 |
| 支付回调 | 沙箱控制台可模拟成功、失败、重复回调、篡改金额 | 先验签，再校验金额，最后用 `WHERE status='PENDING'` 保证幂等 |
| 物流 | 订单详情实时追加物流轨迹 | 快递鸟订阅 → MQ → WebSocket 推送 |
| 秒杀 | 先获取动态地址，再提交，然后轮询结果；页面展示链路追踪 | 令牌桶 → path 校验与风控 → Lua 判重并扣减 → Kafka → 乐观锁落库 |
| 商品改价 | 后台修改 SKU 后可在链路日志里查看缓存操作 | Cache Aside + 延迟双删（500ms） |
| 库存一致性 | 后台「缓存一致性」页校验和修复 | 定时任务比对 Redis 与 DB，以 DB 为准 |

## 5. 已验证

- `npm run typecheck`、`npm run build` 通过。
- 用无头 Chrome 驱动真实页面走通以下流程，全程无控制台错误：
  1. 普通用户登录 → 加入购物车 → 结算 → 提交订单 → 沙箱支付成功；
  2. 管理员在后台看到待发货订单并发货；
  3. 用户订单页收到物流推送 → 确认收货，获得积分；
  4. 后台秒杀活动用 1000 个并发请求模拟抢购，结果无超卖，Redis 与 DB 库存一致。

## 6. 后续

- 后端按 `api-contract.md` 实现，并把 `VITE_USE_MOCK` 设为 `false` 联调。
- 商品图接入 OSS：给 `ProductArt` 传入 `src`。
- 搜索切换到 Elasticsearch 后，`/products` 接口保持不变。
