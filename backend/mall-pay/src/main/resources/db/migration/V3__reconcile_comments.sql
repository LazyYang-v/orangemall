-- 漂移对齐（Phase 4 引入 Flyway 时发现）：线上库 refund_order 表注释乱码（当年 mysql CLI 未指定 utf8mb4 写入）、
-- 若干列注释缺失、payment.refund_no/refunded_amount 列序在 created_at 之后（后追加）。
-- 对齐到 V1 定义；在全新库上为空操作。payment 列序调整需重建表（小表，秒级）。回滚见 db/rollback/U3__reconcile_comments.sql
SET NAMES utf8mb4;
ALTER TABLE payment
  MODIFY refund_no VARCHAR(64) NULL COMMENT '关联退款流水号（refund id）' AFTER paid_at,
  MODIFY refunded_amount INT NOT NULL DEFAULT 0 AFTER refund_no;
ALTER TABLE pay_notify_log COMMENT '支付回调日志（对账用；每天清理 30 天前记录）';
ALTER TABLE refund_order
  MODIFY id BIGINT NOT NULL COMMENT '雪花 ID，同时是退款执行幂等键',
  MODIFY payment_no VARCHAR(32) NULL COMMENT '原支付流水（可空=积分全额抵扣单）',
  MODIFY type VARCHAR(10) NOT NULL COMMENT 'REFUND 退款 / RETURN 退货退款 / EXCHANGE 换货',
  MODIFY reason VARCHAR(200) NOT NULL COMMENT '用户填写的售后原因',
  MODIFY amount INT NOT NULL COMMENT '退款金额（分），= payAmount 全额',
  MODIFY status VARCHAR(20) NOT NULL DEFAULT 'WAIT_APPROVAL' COMMENT 'WAIT_APPROVAL → APPROVED → REFUNDING → DONE / REJECTED / CANCELLED',
  MODIFY prev_status VARCHAR(20) NOT NULL COMMENT '售后申请时订单状态，拒绝/取消后回滚到此',
  MODIFY audit_remark VARCHAR(200) NULL COMMENT '运营审批备注',
  MODIFY audit_by BIGINT NULL COMMENT '审批人 userId',
  MODIFY refund_trade_no VARCHAR(64) NULL COMMENT '渠道退款流水号（沙箱模拟生成）',
  COMMENT '售后/退款单（同一订单仅一笔进行中：业务侧校验，幂等键=refund id）';
