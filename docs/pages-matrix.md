# 页面矩阵（Phase 2 交付）

> 范围：`frontend/src/router/index.ts` 全部路由。更新于 2026-11-06。
> 「375」= `scripts/mobile-audit.mjs`（375×812，登录态）测得 `documentElement.scrollWidth - clientWidth`；
> 「E2E」= `frontend/playwright/tests/*` 覆盖情况。断点统一为 `@media (max-width: 900px)`。

## 商城（MallLayout）

| 路由 | 页面 | 鉴权 | 375 溢出 | Playwright | 主要后端依赖 | 移动端适配要点 |
|---|---|---|---|---|---|---|
| `/` | 首页 | — | 0 | 01 浏览 / 03 移动 | product、marketing | 隐藏左侧类目（导航栏「全部分类」兜底）；hero/promo 单列；服务条 2×2 |
| `/search` | 商品搜索 | — | 0 | 01 / 03 | product | — |
| `/product/:id` | 商品详情 | — | 0 | 01 / 02 加购 / 03 | product、cart、marketing | 画廊整宽；侧栏下移；购买按钮均分；参数表收窄 |
| `/cart` | 购物车 | —（游客本地车） | 0 | 02 / 03 | cart | 隐藏表头与单价列；行内换行；底栏精简 |
| `/checkout?items=sku:n` | 确认订单 | 登录 | 0 | 02 下单 / 03 | order、user、marketing | 地址/券卡单列；汇总整宽 |
| `/pay/:orderId` | 收银台 | 登录 | 0 | 02 沙箱支付 | pay | — |
| `/pay/result/:orderId` | 支付结果 | 登录 | 0 | 02 | order | — |
| `/orders` | 我的订单 | 登录 | 0 | 01 / 03 | order | 顶栏换行 |
| `/orders/:id` | 订单详情 | 登录 | 0 | 02 申请售后 | order、pay(refund) | 状态区纵向；步骤条可横滑；信息双列 → 单列 |
| `/seckill` | 限时秒杀 | — | 0 | 03 | seckill | 场次条横滑；商品单列 |
| `/seckill/:activityId` | 秒杀详情 | — | 0 | — | seckill | — |
| `/coupons` | 领券中心 | — | 0 | 01 / 03 | marketing | — |
| `/points` | 积分商城 | — | 0 | 03 | marketing、user | 6 列 → 2 列 |
| `/brands` | 品牌馆 | — | 0 | 01 / 03 | product | 3 列 → 1 列 |
| `/rank` | 排行榜 | — | 0 | 01 / 03 | product | — |
| `/user` | 个人中心 | 登录 | 0 | 01 / 03 | user、order | 侧栏变顶部横滑 Tab；KPI 换行 |
| `/user/coupons` | 我的优惠券 | 登录 | 0 | 03 | marketing | 单列 |
| `/user/refunds` | 我的售后（新增） | 登录 | 0 | 01 / 02 / 03 | pay(refund) | 操作区换行 |
| `/user/favorites` | 我的收藏 | 登录 | 0 | — | user | 4 列 → 2 列 |
| `/user/addresses` | 收货地址 | 登录 | 0 | 03 | user | — |
| `/login` `/register` | 登录/注册 | — | 未测 | （测试用 API 注入 token） | user | AuthLayout 自带居中卡片 |
| `/404` | 404 | — | 未测 | — | — | — |

全局：`html/body min-width` 由 1320px 改为 320px；`.container` 改为 `min(1280px, 100% - 32px)`；
TopBar / SiteHeader / MainNav / SiteFooter 均有 ≤900px 规则（热搜词与面包屑改横滑，不再逐字折行）。

## 运营后台（AdminLayout，需 ADMIN）

| 路由 | 页面 | Playwright | 说明 |
|---|---|---|---|
| `/admin` | 数据看板 | 03（375 不白屏） | — |
| `/admin/orders` | 订单管理 | — | 关键词改走冗余列 `receiver/phone`（OM-015） |
| `/admin/refunds` | 售后管理（新增） | 02 审批通过 → 已退款 | 审批弹窗：通过/拒绝 + 备注 |
| `/admin/users` | 用户管理（新增） | 02 列表 + 详情/封禁 | 封禁复用风控黑名单 |
| `/admin/products` `/categories` `/coupons` `/seckill` `/cache` `/risk` `/logs` | 既有页面 | — | 浏览器 E2E 脚本覆盖可达性 |

后台定位为桌面端工具：375px 仅保证可打开、不白屏，不承诺无横滑（表格多列）。

## 未覆盖 / 后续

- 登录/注册页、秒杀详情、收藏页未进 Playwright（已在 mobile-audit 中测量 375 溢出）。
- 后台 7 个既有页面无 Playwright 用例，依赖 `/tmp/om-e2e/flow-backend.mjs` 浏览器回归；建议 Phase 3 迁入仓库。
