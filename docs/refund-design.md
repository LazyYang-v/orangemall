# 退款/售后域设计（Phase 1 占位 · Phase 2 实施）

> OM-008（P1 缺失）：订单状态机止步 COMPLETED、无退款流水、无售后审批。本文档是实施前的设计基准，
> 落地时并入 `mall-pay`（决策 D1），规模变大再拆独立服务。

## 1. 数据模型

```sql
-- mall_pay 库新增
CREATE TABLE refund_order (
  id BIGINT PRIMARY KEY COMMENT '雪花 ID',
  order_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  payment_no VARCHAR(32) NULL COMMENT '原支付流水（可空=未支付无需退款）',
  type VARCHAR(10) NOT NULL COMMENT 'REFUND 退款 / RETURN 退货退款 / EXCHANGE 换货',
  reason VARCHAR(200) NOT NULL COMMENT '用户填写的售后原因',
  amount INT NOT NULL COMMENT '退款金额（分），默认= payAmount',
  status VARCHAR(20) NOT NULL DEFAULT 'WAIT_APPROVAL'
        COMMENT 'WAIT_APPROVAL → APPROVED → REFUNDING → DONE / REJECTED → CANCELLED',
  audit_remark VARCHAR(200) NULL COMMENT '运营审批备注',
  audit_by BIGINT NULL COMMENT '审批人 userId',
  audit_at DATETIME(3) NULL,
  refund_trade_no VARCHAR(64) NULL COMMENT '渠道退款流水号（沙箱模拟生成）',
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  KEY idx_user (user_id),
  KEY idx_order (order_id),
  KEY idx_status (status)
) COMMENT '售后/退款单';

-- payment 表补退款语义列
ALTER TABLE payment
  ADD COLUMN refund_no VARCHAR(64) NULL COMMENT '关联退款流水号',
  ADD COLUMN refunded_amount INT NOT NULL DEFAULT 0;
```

## 2. 状态机

```
WAIT_APPROVAL ─ 审批通过(APPROVE) → APPROVED ─ 渠道受理(REFUNDING) → REFUNDING ─ 渠道回调 DIA → DONE
       │
       └─ 审批拒绝(REJECT) → REJECTED ─ 终态
用户在 WAIT_APPROVAL 前可自行 CANCELLED
幂等：同一 order 仅允许一笔进行中的售后单（uk：order_id + status NOT IN 终态 逻辑锁）；
退款执行幂等键 = refund.id（重复回调/AI 重复触发均安全）。
```

关键防线：**任何金额变更必须人审**——Agent/自动化只能创建 WAIT_APPROVAL 状态单。

## 3. 接口（契约增量，全部 Result 语义）

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| POST | `/refunds` | user | 用户发起 `{orderId, type, reason}`；PENDING_PAYMENT 单走既有「取消即原额退」，已支付走售后单 |
| GET | `/refunds` | user | 我的售后列表 |
| GET | `/refunds/{id}` | user | 详情（含审批进度/退款流水） |
| POST | `/admin/refunds/{id}/approve` | admin | 审批通过 `{pass, remark}` |
| GET | `/admin/refunds?status=` | admin | 售后列表 |
| POST | `/payments/notify` | barrow | 渠道退款回调（复用现有验签，type=REFUND_SUCCESS 分支置 DONE + 订单 REFUNDED） |

订单状态机增量：`COMPLETED → REFUND_APPLYING → REFUNDED`（审核中/已退款两个只读展示态，不影响既有完整性校验）。

## 4. 联动与一致性

- 退款 DONE 时：`sku` `restore`（若 type RETURN/REFUND）+ `refund_order` 流水留档 + `payment.refunded_amount` 累加 + ops 日志 + WS 推送。
- **收尾可补偿（Phase 6）**：上面的回补库存与订单 REFUNDED 在售后单 DONE 落库之后执行，原来任一步失败都会抛给渠道回调，
  而重放回调会被「已 DONE」幂等吞掉，于是订单永远停在 REFUND_APPLYING、库存不回补。现在：
  - `refund_order.settled_at`（pay V4）= 收尾完成时间；V4 把历史 DONE 单回填为 `done_at`，避免对无流水的老订单重复回补。
  - 回调里收尾失败只记 `[完成延后]`，回调照常确认；`PayJobs.settleDoneRefunds` 每 60s 重放 DONE ≥ 30s 且 `settled_at` 为空的单。
  - 收尾两步都幂等：商品侧回补按 `stock_ledger` 每单只补一次；订单侧已是 REFUNDED 视为成功。超过 10min 仍未收尾，每单 ALERT 一次。
- 支付↔订单对账 job（`ReconcileJob`）扩一条：处于 REFUNDING 超过 24h 的售后单告警。
- 渠道模拟：`/payments/{no}/sandbox-pay?refund=true` 生成合法退款签名，E2E 可完整演示。

## 5. 验收（Phase 2 DoD）

1. 用户对已支付订单发起售后 → 后台审批 → 原路退回 → 订单展示「已退款」，库存回补，积分/券不重复回滚。
2. 重复审批/重复回调幂等，无重复退款。
3. 退款金额永远等于审批金额（Agent 可发起申请但无权改金额并直接执行）。
