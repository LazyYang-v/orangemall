-- mall_seckill 基线结构（Phase 4 由 deploy/mysql/init/01-schema.sql 拆出；之后的结构变更只追加 V<n>__*.sql，不改本文件）
SET NAMES utf8mb4;

-- Seata AT 回滚日志
CREATE TABLE IF NOT EXISTS undo_log (branch_id BIGINT NOT NULL, xid VARCHAR(128) NOT NULL, context VARCHAR(128) NOT NULL, rollback_info LONGBLOB NOT NULL, log_status INT NOT NULL, log_created DATETIME(6) NOT NULL, log_modified DATETIME(6) NOT NULL, UNIQUE KEY ux_undo_log (xid, branch_id), KEY ix_log_created (log_created)) ENGINE=InnoDB;
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
ALTER TABLE seckill_activity
  ADD COLUMN name VARCHAR(100) NOT NULL DEFAULT '' COMMENT '商品名快照' AFTER spu_id,
  ADD COLUMN art VARCHAR(20) NOT NULL DEFAULT '' COMMENT '商品图标快照' AFTER name,
  ADD COLUMN tint VARCHAR(20) NOT NULL DEFAULT '' COMMENT '商品色调快照' AFTER art,
  ADD COLUMN original_price INT NOT NULL DEFAULT 0 COMMENT 'SKU 原价快照' AFTER seckill_price,
  ADD COLUMN warmed TINYINT NOT NULL DEFAULT 0 COMMENT '是否已完成首次预热（Redis 丢失时以 DB stock 回填，不再重算）' AFTER limit_per_user;

-- OM-019：秒杀 Kafka 死信留档（DLT 监听写入；(dlt_partition, dlt_offset) 唯一保证重复投递幂等）
CREATE TABLE IF NOT EXISTS seckill_dead_letter (
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
