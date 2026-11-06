-- OM-025 压测用户：5000 个，手机号 17700000000 ~ 17700004999，密码统一 123456
-- （复用种子用户 13800000000 的 salt/hash，算法 sha256Hex(salt + ":" + password)）
-- id 段 1770000000000000000 + i，地址 id 段 1770000000100000000 + i，均与业务雪花 ID 不重叠。
-- 幂等：INSERT IGNORE，可重复执行。
-- 执行：docker compose exec -T mysql mysql -uroot -proot --default-character-set=utf8mb4 < bench/sql/seed-bench-users.sql
SET SESSION cte_max_recursion_depth = 10000;
USE mall_user;

INSERT IGNORE INTO user (id, phone, password_hash, salt, nickname, level, points, credit_score, blocked, risk_hits, last_action, role)
WITH RECURSIVE seq(i) AS (SELECT 0 UNION ALL SELECT i + 1 FROM seq WHERE i < 4999)
SELECT 1770000000000000000 + i,
       CONCAT('177', LPAD(i, 8, '0')),
       '3598757b3ef43da46309c607ff038f1f2bce55c47b9743504e244f5406ae8b00',
       '5215bcfac350b7b8',
       CONCAT('压测用户', LPAD(i, 4, '0')),
       1, 0, 650, 0, 0, '正常', 'USER'
FROM seq;

INSERT IGNORE INTO address (id, user_id, receiver, phone, province, city, district, detail, is_default)
WITH RECURSIVE seq(i) AS (SELECT 0 UNION ALL SELECT i + 1 FROM seq WHERE i < 4999)
SELECT 1770000000100000000 + i,
       1770000000000000000 + i,
       CONCAT('压测', LPAD(i, 4, '0')),
       CONCAT('177', LPAD(i, 8, '0')),
       '陕西省', '西安市', '雁塔区', CONCAT('压测路 ', i, ' 号'),
       1
FROM seq;

SELECT COUNT(*) AS bench_users FROM user WHERE phone LIKE '177%' AND id BETWEEN 1770000000000000000 AND 1770000000000004999;
