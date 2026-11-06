-- 库存对账（Phase 6）按时间窗扫订单：closed_at 找「已关闭 / 已取消」，created_at 找「新下的普通单」。
-- 原有 idx_user_time 以 user_id 开头，用不上；归档表只存 90 天前的订单，对账窗口（≤ 24h）不涉及。
CREATE INDEX idx_closed_at ON t_order_0 (closed_at);
CREATE INDEX idx_closed_at ON t_order_1 (closed_at);
CREATE INDEX idx_closed_at ON t_order_2 (closed_at);
CREATE INDEX idx_closed_at ON t_order_3 (closed_at);
CREATE INDEX idx_created_at ON t_order_0 (created_at);
CREATE INDEX idx_created_at ON t_order_1 (created_at);
CREATE INDEX idx_created_at ON t_order_2 (created_at);
CREATE INDEX idx_created_at ON t_order_3 (created_at);
