-- mall_order 基线结构（Phase 4 由 deploy/mysql/init/01-schema.sql 拆出；之后的结构变更只追加 V<n>__*.sql，不改本文件）
SET NAMES utf8mb4;

-- Seata AT 回滚日志
CREATE TABLE IF NOT EXISTS undo_log (branch_id BIGINT NOT NULL, xid VARCHAR(128) NOT NULL, context VARCHAR(128) NOT NULL, rollback_info LONGBLOB NOT NULL, log_status INT NOT NULL, log_created DATETIME(6) NOT NULL, log_modified DATETIME(6) NOT NULL, UNIQUE KEY ux_undo_log (xid, branch_id), KEY ix_log_created (log_created)) ENGINE=InnoDB;
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
