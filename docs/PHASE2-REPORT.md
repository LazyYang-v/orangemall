# Phase 2 实施报告（售后域 · 运营后台 · 移动端 · Playwright）

> 依据 `docs/AUDIT-2026-11-06.md` Phase 2：「移动断点、设计 token、售后域前后端、用户管理/RBAC 雏形、Playwright 冒烟；
> DoD：375px 全流程可用、Playwright CI 稳定」，并入 OM-015（后台检索）。实施时间 2026-11-06，改动均已部署到本机 compose 栈并回归。

## 1. 交付内容

| 编号 | 内容 | 关键改动 | 验证证据 |
|---|---|---|---|
| OM-008 | 售后/退款闭环（按 `refund-design.md`） | mall-pay：`refund_order` 表、`RefundService`（申请/撤回/审批/渠道受理/回调/超时告警）、`RefundController`、`AdminRefundController`；回调复用 `/payments/notify`（`refundId` + `REFUND_SUCCESS`，独立 HMAC）；mall-order：`REFUND_APPLYING`/`REFUNDED` 状态与 `prev_status` 回退、内部接口 `items` / `refund-state`（CAS）；库存回补（换货除外）、`payment.refunded_amount` 累计、WebSocket `REFUND_UPDATE` | API 回归 20 项全绿（正向链路、重复申请、非 admin 403、重复审批 6104、回调 DONE、订单 REFUNDED、库存 +1、拒绝回退 PAID、未支付 6101、撤回回退） |
| OM-008 前端 | 用户/运营两端页面 | 订单详情「申请售后」弹窗（仅退款/退货退款/换货）、`/user/refunds` 我的售后、`/admin/refunds` 售后管理（审批通过/拒绝 + 备注）、字典与类型 | Playwright 02：下单 → 沙箱支付 → 申请售后 → 待审批 → admin 通过 → 轮询到「已退款」 |
| 用户管理 / RBAC 雏形 | `/admin/users` 列表 + 详情 + 封禁 | mall-user `GET /admin/users`、`/admin/users/{id}`；网关路由；后台菜单新增「用户」分组；封禁复用风控黑名单 | Playwright 02：列表、详情/封禁按钮可见 |
| OM-015 | 后台收货人/手机号检索 | 冗余列 `receiver`/`phone` + 独立索引 `idx_receiver`、`idx_phone`（4 分片 + 归档表）；关键词按形态分流：15+ 位数字 → 主键精确，数字 → 手机号前缀，其它 → 收货人前缀；`% _ !` 转义按字面匹配；seed 回填 | 10 万行基准：新方案 `range/const`，扫描 139 / 1262 / 1 行，1.5 ms / 0.9 ms；旧 `%kw%` 方案 `ALL` 扫描 99 310 行，111.6 ms |
| OM-023 | 375px 移动端 | 全局 `min-width 1320px → 320px`、`.container` 流式；TopBar/SiteHeader/MainNav/SiteFooter 及 20+ 页面/组件 `≤900px` 规则（详见 `docs/pages-matrix.md`） | `scripts/mobile-audit.mjs` 19 个页面横向溢出全部 0px；人工截图核对首页/详情/购物车/订单详情/个人中心 |
| Playwright | 冒烟入库 + CI | `frontend/playwright/`（01 浏览 7 例 · 02 交易链路 6 例串行 · 03 移动端 16 例）；`.github/workflows/e2e.yml`（构建后端 → compose 拉栈 → 构建前端 → 跑用例，失败上传报告与服务日志） | 本机关闭重试连续 4 次 29/29 通过；GitHub Actions 已在 PR #1 上运行通过（随 Phase 1–3 合并 main，`f52d35a`） |
| 页面矩阵 | `docs/pages-matrix.md` | 全路由的鉴权、375 结果、E2E 覆盖、依赖与适配要点 | — |

错误码：售后独立号段 `61xx`（6101 不可售后 / 6102 不存在 / 6103 已有进行中 / 6104 已受理或流水不匹配），
避免与秒杀 `7xxx` 冲突；`docs/api-contract.md` 已补 §6.1 售后接口、`/admin/users`、错误码表。

## 2. 过程中发现并修复的问题

1. **Nginx 缓存网关旧 IP → 全站 502**：网关容器重建后 IP 变化，`upstream` 静态解析仍指向旧地址。
   修复：`resolver 127.0.0.11 valid=10s` + `server mall-gateway:8080 resolve`（nginx 1.27.5 支持），保留 keepalive。
2. **OM-015 首版无效**：首版虽加了冗余列，但查询仍是 `%kw%` 且与 `CAST(id AS CHAR)` 做 OR，索引用不上、`ESCAPE` 也未转义输入；已改为上表的分流方案并用 10 万行基准验证。
3. **订单 2120394857100002561 物流轨迹乱码**：Phase 1 修复该单时 mysql 客户端未指定 `utf8mb4`，中文被双重编码。
   已按 `Logistics.java` 模板还原（全库扫描仅此 1 行受影响）；此后所有 SQL 均带 `--default-character-set=utf8mb4`。
4. **全新初始化校验**：在一次性 MySQL 容器中执行 `01-schema.sql + 02-seed.sql`，确认新列/新表/回填全部可用。

## 3. 回归结论

- 售后 API 回归（`/tmp/om-regress-v2.py`）20/20；Playwright 29/29 ×4；16 页浏览器 E2E 通过（秒杀 1000 并发无超卖、缓存一致、页面错误 0）。
- `vue-tsc` + `vite build` 通过；mall-pay / mall-order / mall-user / mall-gateway 均以 `clean package` 重打镜像部署。

## 4. 已知限制 / 未做

- 运营后台按桌面工具定位，375px 只保证可打开，不承诺无横滑。
- RBAC 仍是 `USER/ADMIN` 二元角色，用户管理为雏形（无角色分配、无操作审计细分）。
- 设计 token：沿用 `styles/base.css` 既有颜色/圆角/阴影变量，断点统一为 900px；未扩展间距/字号 token。
- 退款为全额退款；部分退款、退货物流单号回填未实现。
- ~~CI 工作流需推送到 GitHub 后首跑确认~~ → 已在 PR #1 上跑通（ubuntu-latest runner 可拉起整栈）。
