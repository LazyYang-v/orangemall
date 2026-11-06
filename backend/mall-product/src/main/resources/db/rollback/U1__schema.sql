-- 反向补偿 V1__schema（mall_product）：删除基线建的全部表。会丢数据：仅用于开发/CI 往返校验或整库重建，生产禁止执行。
DROP TABLE IF EXISTS notice;
DROP TABLE IF EXISTS favorite;
DROP TABLE IF EXISTS sku;
DROP TABLE IF EXISTS spu;
DROP TABLE IF EXISTS brand;
DROP TABLE IF EXISTS category;
DROP TABLE IF EXISTS undo_log;
