-- mall_pay 基线结构（Phase 4 由 deploy/mysql/init/01-schema.sql 拆出；之后的结构变更只追加 V<n>__*.sql，不改本文件）
SET NAMES utf8mb4;

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
