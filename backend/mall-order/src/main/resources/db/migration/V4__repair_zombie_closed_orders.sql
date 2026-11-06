-- 数据修复：close() 曾把 PENDING_PAYMENT 当作 CAS 目标状态写回（OrderService#close，自首个版本起），
-- 关单只写了 closed_at / close_reason，状态仍是待支付 → 每分钟超时扫描重复关单、重复回补库存。
-- 代码已修复；这里把「已关单但状态仍是待支付」的历史行改成应有的终态（幂等：修复后不会再产生此类行）。
-- 已关单后又被支付成功的订单（status=PAID 等）不在此列，保持不动。
UPDATE t_order_0 SET status = IF(close_reason = '用户主动取消', 'CANCELLED', 'CLOSED') WHERE status = 'PENDING_PAYMENT' AND close_reason IS NOT NULL;
UPDATE t_order_1 SET status = IF(close_reason = '用户主动取消', 'CANCELLED', 'CLOSED') WHERE status = 'PENDING_PAYMENT' AND close_reason IS NOT NULL;
UPDATE t_order_2 SET status = IF(close_reason = '用户主动取消', 'CANCELLED', 'CLOSED') WHERE status = 'PENDING_PAYMENT' AND close_reason IS NOT NULL;
UPDATE t_order_3 SET status = IF(close_reason = '用户主动取消', 'CANCELLED', 'CLOSED') WHERE status = 'PENDING_PAYMENT' AND close_reason IS NOT NULL;
UPDATE t_order_archive SET status = IF(close_reason = '用户主动取消', 'CANCELLED', 'CLOSED') WHERE status = 'PENDING_PAYMENT' AND close_reason IS NOT NULL;
