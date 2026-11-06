-- 反向补偿 V1__schema（mall_user）：删除基线建的全部表。会丢数据：仅用于开发/CI 往返校验或整库重建，生产禁止执行。
DROP TABLE IF EXISTS points_goods;
DROP TABLE IF EXISTS points_record;
DROP TABLE IF EXISTS address;
DROP TABLE IF EXISTS user;
DROP TABLE IF EXISTS undo_log;
