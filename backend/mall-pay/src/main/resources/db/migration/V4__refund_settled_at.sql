-- 退款完成补偿（Phase 6）：售后单 CAS 成 DONE 之后还有两步跨服务操作（回补库存、订单改 REFUNDED）。
-- 原来两步失败后回调重放会因 DONE 已落库被「幂等忽略」，库存不回补、订单停在 REFUND_APPLYING。
-- settled_at = 这两步都完成的时间；DONE 且 settled_at 为空 = 待补偿，由 PayJobs 定时重放（两步都幂等）。
ALTER TABLE refund_order ADD COLUMN settled_at DATETIME(3) NULL COMMENT 'DONE 后回补库存 + 订单 REFUNDED 完成时间；空 = 待补偿' AFTER done_at;
-- 历史 DONE 单视为已完成：其中可能有早于库存流水（product V3）的订单，重放回补会按请求件数再补一次。
UPDATE refund_order SET settled_at = done_at WHERE status = 'DONE';
