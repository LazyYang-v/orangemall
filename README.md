# 橙选商城 OrangeMall

「橙选商城」是一个全栈演示级 B2C 电商项目：**Vue 3 前端 + Spring Cloud 微服务后端 + Docker 基础设施**，
以一个完整的「双十一购物」业务为主线，覆盖从商品、购物车、营销、订单、支付到秒杀的核心链路，
并实现了 Mock 无法演示的六类生产机制（分布式事务 / 异步关单 / Kafka 削峰 / 幂等回调 / TTL 延迟队列 /
缓存一致性巡检）。

## 目录结构

```
frontend/     Vue 3 + TS + Vite SPA（自带 in-browser Mock，可脱离后端独立演示）
backend/      11 个 Maven 模块：mall-common / mall-api / mall-web-starter 共享层
              + 网关与 7 个业务服务（user/product/cart/marketing/order/pay/seckill）
deploy/       docker-compose（基础设施 + app profile 全套容器）与 Nginx/MySQL 配置
docs/         api-contract.md（前端⇄后端契约）· frontend-design.md · backend-design.md
backend/AGENT-BRIEF.md  并行开发广告牌（过程用，可删除）
```

## 快速开始

```bash
# 1) 基础设施（MySQL/Redis/RabbitMQ/Kafka/Nacos/Seata）
cd backend/deploy && docker compose up -d

# 2) 打包 + 全套容器（先完成 backend/ 下各模块 mvn package 与 frontend npm run build）
cd backend/deploy && docker compose --profile app up -d --build

# 3) 打开 http://localhost:8090
#    网关直连：http://localhost:18080
```

前端两种模式：
- `cd frontend && npm run dev` —— 纯前端 Mock（无需任何后端）。
- `cd frontend && npm run dev:backend` —— 关闭 Mock，走本机 18080 真网关（或直接使用 8090 的 Nginx 部署）。

## 演示账号

| 角色 | 账号 | 密码 |
|---|---|---|
| 普通用户 | `13800000000` | `123456`（短信验证码场景填 `123456`） |
| 运营后台 | `18800000000` | `admin123` |

## 核心特性速览

- **契约驱动**：`docs/api-contract.md` 是前后端唯一规范，前端 Mock 与真实后端行为一一对应。
- **网关统一鉴权**：JWT + Redis 黑名单，`/api/admin/**` 角色隔离，链路 ID 全链路透传。
- **订单分片**：4 分片按 `user_id & 3`，用户侧单片路由、管理端 UNION ALL 聚合。
- **Seata AT**：下单「扣库存→锁券→扣积分」跨服务事务，任一失败全局回滚（已实测）。
- **消息**：RabbitMQ TTL+DLX 延迟关单；Kafka 3 分区秒杀削峰（DB 乐观锁兜底、内部重试与补偿）。
- **秒杀四层防护**：令牌桶限流 → path/风控 → Lua 单购+原子扣减 → 异步落库，Redis/DB 在途对账巡检。
- **沙箱支付**：HMAC 签名异步回调、幂等、金额校验、审计日志、MQ 通知换状态。
- **可观测**：OpsLogger（admin 链路日志页）、缓存命中率、风控命中、后台一键模拟抢购压测。

## 文档

- 契约与实现状态：`docs/api-contract.md`
- 后端设计（架构/机制/压测数据/差异说明）：`docs/backend-design.md`
- 前端设计：`docs/frontend-design.md`

> 学习演示项目：短信、支付、物流均为沙箱模拟，未接入真实第三方。
