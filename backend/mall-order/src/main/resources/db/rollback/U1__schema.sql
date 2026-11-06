-- 反向补偿 V1__schema（mall_order）：删除基线建的全部表。会丢数据：仅用于开发/CI 往返校验或整库重建，生产禁止执行。
DROP TABLE IF EXISTS t_order_item_archive;
DROP TABLE IF EXISTS t_order_item_3;
DROP TABLE IF EXISTS t_order_item_2;
DROP TABLE IF EXISTS t_order_item_1;
DROP TABLE IF EXISTS t_order_item_0;
DROP TABLE IF EXISTS t_order_archive;
DROP TABLE IF EXISTS t_order_3;
DROP TABLE IF EXISTS t_order_2;
DROP TABLE IF EXISTS t_order_1;
DROP TABLE IF EXISTS t_order_0;
DROP TABLE IF EXISTS undo_log;
