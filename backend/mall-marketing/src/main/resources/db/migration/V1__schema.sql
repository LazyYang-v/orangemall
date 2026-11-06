-- mall_marketing 基线结构（Phase 4 由 deploy/mysql/init/01-schema.sql 拆出；之后的结构变更只追加 V<n>__*.sql，不改本文件）
SET NAMES utf8mb4;

-- Seata AT 回滚日志
CREATE TABLE IF NOT EXISTS undo_log (branch_id BIGINT NOT NULL, xid VARCHAR(128) NOT NULL, context VARCHAR(128) NOT NULL, rollback_info LONGBLOB NOT NULL, log_status INT NOT NULL, log_created DATETIME(6) NOT NULL, log_modified DATETIME(6) NOT NULL, UNIQUE KEY ux_undo_log (xid, branch_id), KEY ix_log_created (log_created)) ENGINE=InnoDB;
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
