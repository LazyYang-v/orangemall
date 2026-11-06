-- 反向补偿 V1__schema（mall_seckill）：删除基线建的全部表。会丢数据：仅用于开发/CI 往返校验或整库重建，生产禁止执行。
DROP TABLE IF EXISTS seckill_dead_letter;
DROP TABLE IF EXISTS seckill_order;
DROP TABLE IF EXISTS seckill_activity;
DROP TABLE IF EXISTS undo_log;
