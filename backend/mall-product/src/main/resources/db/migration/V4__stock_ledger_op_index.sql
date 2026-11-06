-- 库存对账（Phase 6）按「某时间窗内有哪些订单扣过库存」扫流水：原有 idx_sku_created 以 sku_id 开头，用不上。
CREATE INDEX idx_op_created ON stock_ledger (op, created_at);
