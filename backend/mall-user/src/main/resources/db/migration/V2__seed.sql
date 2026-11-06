-- mall_user 演示种子（由 deploy/mysql/gen-seed.ts 生成；Flyway 冻结后勿重新生成覆盖）
SET NAMES utf8mb4;

INSERT INTO user (id, phone, password_hash, salt, nickname, level, points, credit_score, blocked, risk_hits, last_action, role, created_at) VALUES
  (1880000000000000001, '13800000000', '3598757b3ef43da46309c607ff038f1f2bce55c47b9743504e244f5406ae8b00', '5215bcfac350b7b8', '橙子用户', 3, 2680, 720, 0, 0, '正常', 'USER', NOW(3) - INTERVAL 2592000 SECOND),
  (1880000000000000002, '18800000000', '677f0e9fc2822e088315e93f0d7fea06e216cfc25e4682d6cb6e4016c616e4ed', '0ea8b5be6d23d8ea', '运营管理员', 9, 0, 800, 0, 0, '正常', 'ADMIN', NOW(3) - INTERVAL 5184000 SECOND),
  (1990000000000000101, '19900000101', 'e05388d47ab618eba94e8a333939dedfd0393093c018ca807bd4c139e37d2287', '7bf9b6d8146e6455', '疑似脚本账号A', 1, 0, 120, 1, 386, '1 秒内 40 次秒杀请求', 'USER', NOW(3) - INTERVAL 259200 SECOND),
  (1990000000000000102, '19900000102', '5035cd2e02cba5157e5a85c8d6cc2cdfcfe7d8d4a92d28e3f528b1a7bf93c06a', '819f79212ada1fd3', '疑似黄牛账号B', 1, 0, 260, 0, 92, '同设备 6 个账号下单', 'USER', NOW(3) - INTERVAL 172800 SECOND),
  (1990000000000000103, '19900000103', 'a9827ee31be71535424e40d22327ddba2c74d73d817bbf694d928ab263fdb4cb', '41f6cb70f3aad699', '新注册账号C', 1, 0, 410, 0, 17, '注册 2 分钟即参与秒杀', 'USER', NOW(3) - INTERVAL 86400 SECOND);

INSERT INTO address (id, user_id, receiver, phone, province, city, district, detail, is_default) VALUES
  (1880000000000100001, 1880000000000000001, '张三', '13800000000', '陕西省', '汉中市', '汉台区', '天汉大道 88 号 橙选小区 3 栋 1201', 1),
  (1880000000000100002, 1880000000000000001, '张三', '13800000000', '陕西省', '西安市', '雁塔区', '科技路 10 号 软件园 B 座 6 层', 0);

INSERT INTO points_record (id, user_id, change_amount, reason, created_at) VALUES
  (1880000000000200001, 1880000000000000001, 200, '新用户注册奖励', NOW(3) - INTERVAL 1728000 SECOND),
  (1880000000000200002, 1880000000000000001, 2480, '历史订单确认收货', NOW(3) - INTERVAL 518400 SECOND);

INSERT INTO points_goods (id, name, art, tint, points, stock, coupon_template_id, coupon_amount, coupon_title, sort) VALUES
  ('PG1', '5 元无门槛券', 'book', '#ff6a00', 500, 999, 'CT04', 500, '积分兑换 5 元券', 1),
  ('PG2', '小橙定制帆布袋', 'luggage', '#f3a25b', 1200, 120, NULL, NULL, NULL, 2),
  ('PG3', '每日坚果体验装', 'snack', '#d99a4a', 800, 300, NULL, NULL, NULL, 3),
  ('PG4', '声悦 Buds 3 耳机', 'earbuds', '#e9edf3', 19900, 8, NULL, NULL, NULL, 4),
  ('PG5', '挂耳咖啡 10 包', 'coffee', '#8b5a3c', 990, 260, NULL, NULL, NULL, 5),
  ('PG6', '积木钥匙扣', 'toy', '#f2b134', 600, 0, NULL, NULL, NULL, 6);
