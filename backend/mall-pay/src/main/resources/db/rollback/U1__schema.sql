-- 反向补偿 V1__schema（mall_pay）：删除基线建的全部表。会丢数据：仅用于开发/CI 往返校验或整库重建，生产禁止执行。
DROP TABLE IF EXISTS refund_order;
DROP TABLE IF EXISTS pay_notify_log;
DROP TABLE IF EXISTS payment;
