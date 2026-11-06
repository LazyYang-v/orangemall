-- 漂移对齐（Phase 4 引入 Flyway 时发现）：线上库 receiver/phone 冗余列由 Phase 2 ALTER 追加，缺列注释。
-- 只改元数据（ALGORITHM=INSTANT 级别，不重建表）；在全新库上为空操作。回滚见 db/rollback/U3__reconcile_comments.sql
SET NAMES utf8mb4;
ALTER TABLE t_order_0 MODIFY receiver VARCHAR(40) NULL COMMENT '冗余列（OM-015）：收货人，避免 JSON_EXTRACT 检索', MODIFY phone VARCHAR(20) NULL COMMENT '冗余列（OM-015）：联系电话';
ALTER TABLE t_order_1 MODIFY receiver VARCHAR(40) NULL COMMENT '冗余列（OM-015）：收货人，避免 JSON_EXTRACT 检索', MODIFY phone VARCHAR(20) NULL COMMENT '冗余列（OM-015）：联系电话';
ALTER TABLE t_order_2 MODIFY receiver VARCHAR(40) NULL COMMENT '冗余列（OM-015）：收货人，避免 JSON_EXTRACT 检索', MODIFY phone VARCHAR(20) NULL COMMENT '冗余列（OM-015）：联系电话';
ALTER TABLE t_order_3 MODIFY receiver VARCHAR(40) NULL COMMENT '冗余列（OM-015）：收货人，避免 JSON_EXTRACT 检索', MODIFY phone VARCHAR(20) NULL COMMENT '冗余列（OM-015）：联系电话';
ALTER TABLE t_order_archive MODIFY receiver VARCHAR(40) NULL COMMENT '冗余列（OM-015）：收货人，避免 JSON_EXTRACT 检索', MODIFY phone VARCHAR(20) NULL COMMENT '冗余列（OM-015）：联系电话';
