-- 库存流水（Phase 5）：普通订单的扣减 / 回补按「订单 × SKU × 动作」唯一记账。
--   DEDUCT  下单扣减，与 UPDATE sku 同在 Seata AT 分支里：全局回滚时流水一并撤销，流水存在 ⇔ 扣减已生效
--   RESTORE 关单 / 退款回补：唯一键挡住重复回补（MQ 延迟消息、DLQ 重驱动、超时扫描、退款回调重放）；
--           回补数量取该订单的 DEDUCT 记录，不信任调用方传入的件数
-- 秒杀库存在 seckill 服务自管，不进此表。
CREATE TABLE stock_ledger (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  sku_id BIGINT NOT NULL,
  op VARCHAR(8) NOT NULL COMMENT 'DEDUCT 下单扣减 / RESTORE 关单或退款回补',
  qty INT NOT NULL COMMENT '件数（正数）',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_order_sku_op (order_id, sku_id, op),
  KEY idx_sku_created (sku_id, created_at)
) COMMENT '库存流水（普通订单扣减 / 回补，按订单幂等）';
