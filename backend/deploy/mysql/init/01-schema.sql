-- 橙选商城 · 库表结构（每个微服务独立库；所有表含 created_at / updated_at）
-- 时区：MySQL default-time-zone='+08:00'，JDBC serverTimezone=Asia/Shanghai，实体使用 java.time.Instant
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS mall_user DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS mall_product DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS mall_marketing DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS mall_order DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS mall_pay DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS mall_seckill DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Seata AT 回滚日志（参与全局事务的库都需要）
CREATE TABLE IF NOT EXISTS mall_user.undo_log (branch_id BIGINT NOT NULL, xid VARCHAR(128) NOT NULL, context VARCHAR(128) NOT NULL, rollback_info LONGBLOB NOT NULL, log_status INT NOT NULL, log_created DATETIME(6) NOT NULL, log_modified DATETIME(6) NOT NULL, UNIQUE KEY ux_undo_log (xid, branch_id), KEY ix_log_created (log_created)) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS mall_product.undo_log LIKE mall_user.undo_log;
CREATE TABLE IF NOT EXISTS mall_marketing.undo_log LIKE mall_user.undo_log;
CREATE TABLE IF NOT EXISTS mall_order.undo_log LIKE mall_user.undo_log;
CREATE TABLE IF NOT EXISTS mall_seckill.undo_log LIKE mall_user.undo_log;

/* ======================= 用户服务 mall_user ======================= */
USE mall_user;
CREATE TABLE user (
  id BIGINT PRIMARY KEY COMMENT '雪花 ID',
  phone VARCHAR(20) NOT NULL,
  password_hash CHAR(64) NOT NULL COMMENT 'sha256Hex(salt + ":" + password)',
  salt CHAR(16) NOT NULL,
  nickname VARCHAR(40) NOT NULL,
  avatar VARCHAR(255) NULL,
  level INT NOT NULL DEFAULT 1,
  points INT NOT NULL DEFAULT 0 COMMENT '积分余额',
  credit_score INT NOT NULL DEFAULT 600 COMMENT '信用分（防刷）',
  blocked TINYINT NOT NULL DEFAULT 0 COMMENT '风控黑名单',
  risk_hits INT NOT NULL DEFAULT 0 COMMENT '风控命中次数',
  last_action VARCHAR(100) NOT NULL DEFAULT '正常',
  role VARCHAR(10) NOT NULL DEFAULT 'USER',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_phone (phone),
  KEY idx_credit (credit_score)
) COMMENT '用户';

CREATE TABLE address (
  id BIGINT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  receiver VARCHAR(40) NOT NULL,
  phone VARCHAR(20) NOT NULL,
  province VARCHAR(40) NOT NULL,
  city VARCHAR(40) NOT NULL,
  district VARCHAR(40) NOT NULL,
  detail VARCHAR(200) NOT NULL,
  is_default TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  KEY idx_user (user_id)
) COMMENT '收货地址';

CREATE TABLE points_record (
  id BIGINT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  change_amount INT NOT NULL COMMENT '正数获得，负数消耗',
  reason VARCHAR(100) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  KEY idx_user_time (user_id, created_at)
) COMMENT '积分流水';

CREATE TABLE points_goods (
  id VARCHAR(16) PRIMARY KEY,
  name VARCHAR(60) NOT NULL,
  art VARCHAR(20) NOT NULL,
  tint VARCHAR(10) NOT NULL,
  points INT NOT NULL,
  stock INT NOT NULL,
  coupon_template_id VARCHAR(16) NULL COMMENT '兑换后发放的券模板（虚拟商品）',
  coupon_amount INT NULL COMMENT '覆盖券面额（分）',
  coupon_title VARCHAR(60) NULL,
  sort INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) COMMENT '积分商城商品';

/* ======================= 商品服务 mall_product ======================= */
USE mall_product;
CREATE TABLE category (
  id INT PRIMARY KEY,
  parent_id INT NOT NULL DEFAULT 0 COMMENT '邻接表，0 为根',
  name VARCHAR(40) NOT NULL,
  icon VARCHAR(20) NULL,
  sort INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  KEY idx_parent (parent_id)
) COMMENT '类目（邻接表）';

CREATE TABLE brand (
  id INT PRIMARY KEY,
  name VARCHAR(40) NOT NULL,
  shop_name VARCHAR(60) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) COMMENT '品牌';

CREATE TABLE spu (
  id BIGINT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  subtitle VARCHAR(200) NOT NULL,
  category_id INT NOT NULL,
  brand_id INT NOT NULL,
  art VARCHAR(20) NOT NULL COMMENT '前端矢量插画类型',
  tint VARCHAR(10) NOT NULL,
  image_url VARCHAR(255) NULL,
  min_price INT NOT NULL COMMENT '分，冗余 = min(sku.price)',
  max_original_price INT NOT NULL,
  sales INT NOT NULL DEFAULT 0,
  rating DECIMAL(3,1) NOT NULL DEFAULT 5.0,
  tags VARCHAR(255) NOT NULL DEFAULT '[]' COMMENT 'JSON 数组',
  status VARCHAR(10) NOT NULL DEFAULT 'ON_SALE',
  spec_groups TEXT NOT NULL COMMENT 'JSON [{name,values}]',
  detail TEXT NOT NULL COMMENT 'JSON 段落数组',
  params TEXT NOT NULL COMMENT 'JSON [[k,v]]',
  is_new TINYINT NOT NULL DEFAULT 0,
  shop_name VARCHAR(60) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  KEY idx_category (category_id),
  KEY idx_brand (brand_id),
  KEY idx_status_sales (status, sales)
) COMMENT 'SPU';

CREATE TABLE sku (
  id BIGINT PRIMARY KEY,
  spu_id BIGINT NOT NULL,
  specs VARCHAR(500) NOT NULL COMMENT 'JSON {规格名: 规格值}，保持规格组顺序',
  price INT NOT NULL,
  original_price INT NOT NULL,
  stock INT NOT NULL,
  sales INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  KEY idx_spu (spu_id)
) COMMENT 'SKU（库存扣减：UPDATE ... WHERE stock >= n）';

CREATE TABLE favorite (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  spu_id BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_user_spu (user_id, spu_id)
) COMMENT '收藏';

CREATE TABLE notice (
  id VARCHAR(16) PRIMARY KEY,
  title VARCHAR(100) NOT NULL,
  date VARCHAR(10) NOT NULL,
  content VARCHAR(500) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) COMMENT '公告';

/* ======================= 营销服务 mall_marketing ======================= */
USE mall_marketing;
CREATE TABLE coupon_template (
  id VARCHAR(16) PRIMARY KEY,
  title VARCHAR(60) NOT NULL,
  type VARCHAR(20) NOT NULL COMMENT 'FULL_REDUCTION / DISCOUNT / NO_THRESHOLD',
  threshold INT NOT NULL DEFAULT 0 COMMENT '分',
  amount INT NOT NULL COMMENT '减免分；折扣券 90 = 9 折',
  scope VARCHAR(60) NOT NULL,
  scope_root_id INT NULL COMMENT '适用根类目，NULL = 全场',
  total INT NOT NULL,
  received INT NOT NULL DEFAULT 0,
  valid_days INT NOT NULL,
  per_user_limit INT NOT NULL DEFAULT 1,
  start_at DATETIME(3) NOT NULL,
  end_at DATETIME(3) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) COMMENT '券模板';

CREATE TABLE user_coupon (
  id BIGINT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  template_id VARCHAR(16) NOT NULL,
  title VARCHAR(60) NOT NULL,
  type VARCHAR(20) NOT NULL,
  threshold INT NOT NULL,
  amount INT NOT NULL,
  scope VARCHAR(60) NOT NULL,
  scope_root_id INT NULL,
  status VARCHAR(10) NOT NULL DEFAULT 'UNUSED' COMMENT 'UNUSED / LOCKED / USED / EXPIRED',
  order_id BIGINT NULL COMMENT '锁定/核销的订单',
  valid_from DATETIME(3) NOT NULL,
  valid_to DATETIME(3) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  KEY idx_user_status (user_id, status),
  KEY idx_user_template (user_id, template_id)
) COMMENT '用户券';

/* ======================= 订单服务 mall_order（按 user_id & 3 分 4 表） ======================= */
USE mall_order;
CREATE TABLE t_order_0 (
  id BIGINT PRIMARY KEY COMMENT '雪花 ID，低 2 位基因 = user_id & 3',
  user_id BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL COMMENT 'PENDING_PAYMENT/PAID/SHIPPED/COMPLETED/CANCELLED/CLOSED',
  source VARCHAR(10) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL / SECKILL',
  goods_amount INT NOT NULL,
  freight INT NOT NULL DEFAULT 0,
  full_reduction INT NOT NULL DEFAULT 0,
  coupon_deduct INT NOT NULL DEFAULT 0,
  points_deduct INT NOT NULL DEFAULT 0,
  seckill_deduct INT NOT NULL DEFAULT 0,
  pay_amount INT NOT NULL,
  coupon_id BIGINT NULL,
  points_used INT NOT NULL DEFAULT 0,
  remark VARCHAR(200) NULL,
  address_json TEXT NOT NULL COMMENT '收货地址快照',
  receiver VARCHAR(40) NULL COMMENT '冗余列（OM-015）：收货人，避免 JSON_EXTRACT 检索',
  phone VARCHAR(20) NULL COMMENT '冗余列（OM-015）：联系电话',
  activity_id VARCHAR(48) NULL COMMENT '秒杀活动',
  pay_deadline DATETIME(3) NOT NULL,
  paid_at DATETIME(3) NULL,
  shipped_at DATETIME(3) NULL,
  completed_at DATETIME(3) NULL,
  closed_at DATETIME(3) NULL,
  close_reason VARCHAR(100) NULL,
  pay_channel VARCHAR(10) NULL,
  payment_no VARCHAR(32) NULL,
  logistics_json TEXT NULL COMMENT '物流信息与轨迹 {company,trackingNo,state,traces[]}',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  KEY idx_user_time (user_id, created_at),
  KEY idx_status (status),
  KEY idx_receiver (receiver),
  KEY idx_phone (phone)
) COMMENT '订单分表 0';
CREATE TABLE t_order_1 LIKE t_order_0;
CREATE TABLE t_order_2 LIKE t_order_0;
CREATE TABLE t_order_3 LIKE t_order_0;
CREATE TABLE t_order_archive LIKE t_order_0;

CREATE TABLE t_order_item_0 (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  sku_id BIGINT NOT NULL,
  spu_id BIGINT NOT NULL,
  name VARCHAR(100) NOT NULL,
  spec_text VARCHAR(200) NOT NULL,
  art VARCHAR(20) NOT NULL,
  tint VARCHAR(10) NOT NULL,
  price INT NOT NULL,
  count INT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  KEY idx_order (order_id)
) COMMENT '订单明细分表 0';
CREATE TABLE t_order_item_1 LIKE t_order_item_0;
CREATE TABLE t_order_item_2 LIKE t_order_item_0;
CREATE TABLE t_order_item_3 LIKE t_order_item_0;
CREATE TABLE t_order_item_archive LIKE t_order_item_0;

/* ======================= 支付服务 mall_pay ======================= */
USE mall_pay;
CREATE TABLE payment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  payment_no VARCHAR(32) NOT NULL COMMENT '支付流水号',
  order_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  channel VARCHAR(10) NOT NULL COMMENT 'ALIPAY / WECHAT',
  amount INT NOT NULL,
  status VARCHAR(10) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SUCCESS/FAILED/CLOSED',
  qr_code VARCHAR(255) NOT NULL,
  expire_at DATETIME(3) NOT NULL,
  callback_count INT NOT NULL DEFAULT 0,
  paid_at DATETIME(3) NULL,
  refund_no VARCHAR(64) NULL COMMENT '关联退款流水号（refund id）',
  refunded_amount INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_payment_no (payment_no) COMMENT '幂等：流水号唯一',
  KEY idx_order (order_id)
) COMMENT '支付记录';

CREATE TABLE pay_notify_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  payment_no VARCHAR(32) NOT NULL,
  trade_status VARCHAR(20) NULL,
  amount INT NULL,
  sign_ok TINYINT NOT NULL,
  result VARCHAR(40) NOT NULL COMMENT 'SUCCESS / IDEMPOTENT / BAD_SIGN / AMOUNT_MISMATCH / ...',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  KEY idx_payment (payment_no),
  KEY idx_created (created_at)
) COMMENT '支付回调日志（对账用；每天清理 30 天前记录）';

-- refund_order（docs/refund-design.md；先并入 mall_pay，规模变大再拆独立服务）
CREATE TABLE refund_order (
  id BIGINT PRIMARY KEY COMMENT '雪花 ID，同时是退款执行幂等键',
  order_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  payment_no VARCHAR(32) NULL COMMENT '原支付流水（可空=积分全额抵扣单）',
  type VARCHAR(10) NOT NULL COMMENT 'REFUND 退款 / RETURN 退货退款 / EXCHANGE 换货',
  reason VARCHAR(200) NOT NULL COMMENT '用户填写的售后原因',
  amount INT NOT NULL COMMENT '退款金额（分），= payAmount 全额',
  status VARCHAR(20) NOT NULL DEFAULT 'WAIT_APPROVAL'
        COMMENT 'WAIT_APPROVAL → APPROVED → REFUNDING → DONE / REJECTED / CANCELLED',
  prev_status VARCHAR(20) NOT NULL COMMENT '售后申请时订单状态，拒绝/取消后回滚到此',
  audit_remark VARCHAR(200) NULL COMMENT '运营审批备注',
  audit_by BIGINT NULL COMMENT '审批人 userId',
  audit_at DATETIME(3) NULL,
  refund_trade_no VARCHAR(64) NULL COMMENT '渠道退款流水号（沙箱模拟生成）',
  done_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  KEY idx_user (user_id, created_at),
  KEY idx_order (order_id),
  KEY idx_status (status, updated_at)
) COMMENT '售后/退款单（同一订单仅一笔进行中：业务侧校验，幂等键=refund id）';

/* ======================= 秒杀服务 mall_seckill ======================= */
USE mall_seckill;
CREATE TABLE seckill_activity (
  id VARCHAR(48) PRIMARY KEY COMMENT 'activityId = {sessionId}-{skuId}',
  session_id VARCHAR(24) NOT NULL COMMENT 'S{startMs}',
  sku_id BIGINT NOT NULL,
  spu_id BIGINT NOT NULL,
  seckill_price INT NOT NULL,
  total_stock INT NOT NULL,
  stock INT NOT NULL COMMENT 'DB 库存（乐观锁 UPDATE ... WHERE stock > 0）',
  limit_per_user INT NOT NULL DEFAULT 1,
  start_at DATETIME(3) NOT NULL,
  end_at DATETIME(3) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  KEY idx_session (session_id),
  KEY idx_time (start_at)
) COMMENT '秒杀活动（每 2 小时一场，由定时任务滚动生成）';

CREATE TABLE seckill_order (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  activity_id VARCHAR(48) NOT NULL,
  user_id BIGINT NOT NULL,
  order_id BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_activity_user (activity_id, user_id) COMMENT 'DB 层一人一单兜底'
) COMMENT '秒杀成交记录';

-- mall-seckill：活动行保存商品快照（名称/图标/色调/原价，秒杀列表不再依赖商品服务）+ 预热标记
ALTER TABLE mall_seckill.seckill_activity
  ADD COLUMN name VARCHAR(100) NOT NULL DEFAULT '' COMMENT '商品名快照' AFTER spu_id,
  ADD COLUMN art VARCHAR(20) NOT NULL DEFAULT '' COMMENT '商品图标快照' AFTER name,
  ADD COLUMN tint VARCHAR(20) NOT NULL DEFAULT '' COMMENT '商品色调快照' AFTER art,
  ADD COLUMN original_price INT NOT NULL DEFAULT 0 COMMENT 'SKU 原价快照' AFTER seckill_price,
  ADD COLUMN warmed TINYINT NOT NULL DEFAULT 0 COMMENT '是否已完成首次预热（Redis 丢失时以 DB stock 回填，不再重算）' AFTER limit_per_user;

-- OM-019：秒杀 Kafka 死信留档（DLT 监听写入；(dlt_partition, dlt_offset) 唯一保证重复投递幂等）
CREATE TABLE IF NOT EXISTS mall_seckill.seckill_dead_letter (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  activity_id VARCHAR(48) NULL,
  user_id BIGINT NULL,
  reason VARCHAR(32) NOT NULL COMMENT 'POISON 非 JSON / INVALID 缺字段 / EXHAUSTED 重试耗尽已补偿 / COMPENSATE_FAILED 补偿失败',
  error VARCHAR(500) NULL,
  payload TEXT NOT NULL,
  src_partition INT NULL,
  src_offset BIGINT NULL,
  dlt_partition INT NOT NULL COMMENT 'DLT 发送失败时直接落库：-1 - 源分区',
  dlt_offset BIGINT NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'NEW' COMMENT 'NEW / RESOLVED',
  remark VARCHAR(200) NULL,
  resolved_by BIGINT NULL,
  resolved_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_dlt_pos (dlt_partition, dlt_offset),
  KEY idx_status (status, created_at)
) COMMENT '秒杀死信';
