-- mall_marketing 演示种子（由 deploy/mysql/gen-seed.ts 生成；Flyway 冻结后勿重新生成覆盖）
SET NAMES utf8mb4;

INSERT INTO coupon_template (id, title, type, threshold, amount, scope, scope_root_id, total, received, valid_days, per_user_limit, start_at, end_at) VALUES
  ('CT01', '双11 全场通用券', 'FULL_REDUCTION', 49900, 5000, '全场通用（秒杀商品除外）', NULL, 10000, 6820, 7, 1, NOW(3) - INTERVAL 86400 SECOND, NOW(3) + INTERVAL 864000 SECOND),
  ('CT02', '双11 大额神券', 'FULL_REDUCTION', 99900, 10000, '全场通用（秒杀商品除外）', NULL, 5000, 4410, 7, 1, NOW(3) - INTERVAL 86400 SECOND, NOW(3) + INTERVAL 864000 SECOND),
  ('CT03', '数码家电品类券', 'FULL_REDUCTION', 300000, 30000, '限数码家电品类', 1, 2000, 1200, 5, 1, NOW(3) - INTERVAL 86400 SECOND, NOW(3) + INTERVAL 518400 SECOND),
  ('CT04', '新人无门槛券', 'NO_THRESHOLD', 0, 1000, '新用户专享', NULL, 20000, 9300, 3, 1, NOW(3) - INTERVAL 86400 SECOND, NOW(3) + INTERVAL 1728000 SECOND),
  ('CT05', '美妆个护 9 折券', 'DISCOUNT', 20000, 90, '限美妆个护品类', 4, 3000, 2990, 7, 1, NOW(3) - INTERVAL 86400 SECOND, NOW(3) + INTERVAL 691200 SECOND),
  ('CT06', '食品生鲜满减券', 'FULL_REDUCTION', 9900, 1500, '限食品生鲜品类', 5, 8000, 3100, 7, 2, NOW(3) - INTERVAL 86400 SECOND, NOW(3) + INTERVAL 777600 SECOND),
  ('CT07', '服饰鞋包满减券', 'FULL_REDUCTION', 39900, 6000, '限服饰鞋包品类', 2, 6000, 6000, 7, 1, NOW(3) - INTERVAL 86400 SECOND, NOW(3) + INTERVAL 777600 SECOND),
  ('CT08', '家居生活满减券', 'FULL_REDUCTION', 199900, 20000, '限家居生活品类', 3, 1500, 400, 7, 1, NOW(3) - INTERVAL 86400 SECOND, NOW(3) + INTERVAL 777600 SECOND);
