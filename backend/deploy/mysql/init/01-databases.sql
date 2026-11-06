-- 橙选商城 · MySQL 首次初始化（仅建库）
-- Phase 4 起库表结构与演示种子由各服务的 Flyway 迁移管理：
--   backend/mall-<svc>/src/main/resources/db/migration/V1__schema.sql、V2__seed.sql、V3…（服务启动时自动执行）
--   回滚补偿：同目录 ../rollback/U<n>__*.sql；往返校验：scripts/db/migration-roundtrip.sh
-- 时区：MySQL default-time-zone='+08:00'，JDBC serverTimezone=Asia/Shanghai，实体使用 java.time.Instant
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS mall_user DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS mall_product DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS mall_marketing DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS mall_order DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS mall_pay DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS mall_seckill DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
