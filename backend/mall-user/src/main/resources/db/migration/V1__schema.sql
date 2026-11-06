-- mall_user 基线结构（Phase 4 由 deploy/mysql/init/01-schema.sql 拆出；之后的结构变更只追加 V<n>__*.sql，不改本文件）
SET NAMES utf8mb4;

-- Seata AT 回滚日志
CREATE TABLE IF NOT EXISTS undo_log (branch_id BIGINT NOT NULL, xid VARCHAR(128) NOT NULL, context VARCHAR(128) NOT NULL, rollback_info LONGBLOB NOT NULL, log_status INT NOT NULL, log_created DATETIME(6) NOT NULL, log_modified DATETIME(6) NOT NULL, UNIQUE KEY ux_undo_log (xid, branch_id), KEY ix_log_created (log_created)) ENGINE=InnoDB;
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
