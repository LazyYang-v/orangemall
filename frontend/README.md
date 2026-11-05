# 橙选商城 OrangeMall · 前端

Vue 3 + TypeScript + Vite 的 B2C 商城前端。视觉参考 `../橙选商城双十一购物首页.png`。
**后端尚未开发**：内置一个浏览器内 Mock 后端，无需任何服务也能走完下单 → 支付 → 发货 → 物流推送 → 收货、秒杀等完整链路。

## 运行

```bash
npm install
npm run dev        # http://localhost:5173 （Mock 模式）
npm run typecheck  # vue-tsc
npm run build      # 类型检查 + 生产构建（生产构建默认关闭 Mock，走网关）
```

| 环境变量 | 说明 |
|---|---|
| `VITE_USE_MOCK` | `true` 使用浏览器内 Mock；`false` 请求真实网关 |
| `VITE_GATEWAY_URL` | 网关地址，dev 下 `/api`、`/ws` 代理到这里（默认 `http://localhost:8080`） |
| `VITE_API_BASE` | 接口前缀，默认 `/api` |

### 演示账号（Mock）

| 角色 | 手机号 | 密码 | 说明 |
|---|---|---|---|
| 普通用户 | 13800000000 | 123456 | 有历史订单、积分 2680 |
| 运营管理员 | 18800000000 | admin123 | 登录后进入 `/admin` |

短信验证码固定为 `123456`。Mock 数据存在 localStorage `om_mock_db_v1` 中，后台右上角「重置 Mock」可一键恢复。

## 页面地图

| 路由 | 页面 | 登录 |
|---|---|---|
| `/` | 首页：类目菜单、Banner、用户面板、秒杀/领券/新品、猜你喜欢（无限滚动） | |
| `/search` | 搜索 / 类目列表：品牌、价格区间、排序、分页 | |
| `/product/:id` | 商品详情：SKU 联动（无货规格置灰）、收藏、加购、立即购买 | |
| `/cart` | 购物车：按店铺分组、失效商品、结算前预校验；未登录存本地、登录后合并 | |
| `/checkout` | 确认订单：地址、优惠券、积分、满减、价格明细、防重提交 | ✓ |
| `/pay/:orderId` | 收银台：倒计时、沙箱控制台（成功 / 失败 / 重复回调 / 篡改金额） | ✓ |
| `/pay/result/:orderId` | 支付结果 | ✓ |
| `/orders`、`/orders/:id` | 订单列表与详情：状态时间线、WebSocket 物流推送、取消 / 确认收货 | ✓ |
| `/seckill`、`/seckill/:activityId` | 秒杀场次与详情：取动态地址 → 提交 → 轮询结果，附链路追踪面板 | 抢购时 |
| `/coupons` | 领券中心 | 领取时 |
| `/points` | 积分商城与明细 | 兑换时 |
| `/brands`、`/rank` | 品牌馆、热销排行榜 | |
| `/user`（`/coupons`、`/favorites`、`/addresses`） | 个人中心：总览、我的优惠券、收藏、收货地址 | ✓ |
| `/login`、`/register` | 登录（密码 / 短信）、注册 | |
| `/admin/*` | 运营后台：看板、订单发货、商品/SKU、类目树、优惠券、秒杀（预热 / 并发模拟 / 拦截漏斗）、缓存一致性、风控、链路日志 | 管理员 |

## 目录结构

```
src/
  api/          按微服务划分的接口模块（user / product / cart / order / pay / seckill / marketing / admin）
  mock/         浏览器内 Mock 后端：seed 种子数据、db 持久化、server 路由、adapter（axios 适配器）、bus（WebSocket 替身）
  types/        领域模型（domain.ts）与推送消息（notify.ts），可对照生成 Java DTO
  stores/       Pinia：user / cart / app(类目树)
  composables/  useCountdown、useNotify（WebSocket 实时推送）
  components/   common 通用 · layout 布局 · home 首页模块 · order 订单 · admin 后台图表
  layouts/      MallLayout / AuthLayout / AdminLayout
  views/        页面
  styles/       base.css（设计变量 + 原子类）、admin.css
```

## 对接真实后端

1. 后端按 [`../docs/api-contract.md`](../docs/api-contract.md) 实现接口（与 `src/mock/server.ts` 一一对应）。
2. 把 `.env.development` 中的 `VITE_USE_MOCK` 设为 `false`。
3. 后台中「模拟超时关单」「模拟抢购」「重置 Mock」三项仅用于演示，对应接口在生产环境不需要实现。

设计说明见 [`../docs/frontend-design.md`](../docs/frontend-design.md)。
