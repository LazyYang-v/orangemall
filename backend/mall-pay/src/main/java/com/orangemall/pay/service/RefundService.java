package com.orangemall.pay.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.orangemall.api.client.OrderInnerClient;
import com.orangemall.api.client.ProductInnerClient;
import com.orangemall.api.dto.OrderBrief;
import com.orangemall.api.dto.RefundStateReq;
import com.orangemall.api.dto.StockItem;
import com.orangemall.common.BizException;
import com.orangemall.common.IdGenerator;
import com.orangemall.common.PageResult;
import com.orangemall.pay.entity.Payment;
import com.orangemall.pay.entity.RefundOrder;
import com.orangemall.pay.mapper.PayMapper;
import com.orangemall.pay.mapper.RefundMapper;
import com.orangemall.web.Notifier;
import com.orangemall.web.OpsLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 售后/退款域（docs/refund-design.md；先并入 mall_pay）。
 * 状态机：WAIT_APPROVAL →(审批) APPROVED →(渠道受理) REFUNDING →(渠道回调) DONE；拒绝 REJECTED；申请时用户可 CANCELLED。
 * 防线：任何金额变更必须人审——Agent/自动化只能创建 WAIT_APPROVAL 状态单。
 * 幂等：refund.id 即退款执行幂等键（重复回调/重复审批不产生二次退款）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundService {
    private static final List<String> ACTIVE = List.of("WAIT_APPROVAL", "APPROVED", "REFUNDING");
    private static final List<String> APPLY_ALLOWED = List.of("PAID", "SHIPPED", "COMPLETED");
    /** 沙箱渠道回调延迟（真实环境为支付渠道异步推送） */
    private static final long CHANNEL_DELAY_SEC = 2;

    private final RefundMapper refunds;
    private final PayMapper payMapper;
    private final OrderInnerClient orders;
    private final ProductInnerClient product;
    private final Notifier notifier;
    private final OpsLogger ops;
    private final SandboxSigner signer;
    private final ScheduledExecutorService channelSim =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "sandbox-refund-channel");
                t.setDaemon(true);
                return t;
            });

    /* ============================ 用户侧 ============================ */

    /** 发起售后：只允许 PAID/SHIPPED/COMPLETED；同一订单仅一笔进行中；金额= payAmount 全额 */
    public Map<String, Object> create(long userId, Long orderId, String type, String reason) {
        OrderBrief brief = orders.brief(orderId).unwrap();
        if (!brief.userId().equals(userId)) throw new BizException(6102, "售后单不存在");
        if (!APPLY_ALLOWED.contains(brief.status())) throw new BizException(6101, "当前订单状态不可发起售后");
        if (refunds.selectCount(new LambdaQueryWrapper<RefundOrder>()
                .eq(RefundOrder::getOrderId, orderId).in(RefundOrder::getStatus, ACTIVE)) > 0) {
            throw new BizException(6103, "该订单已有进行中的售后单");
        }
        String refundType = "REFUND".equalsIgnoreCase(type) || "RETURN".equalsIgnoreCase(type) || "EXCHANGE".equalsIgnoreCase(type)
                ? type.toUpperCase() : "REFUND";
        Payment pay = payMapper.selectList(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getOrderId, orderId).eq(Payment::getStatus, "SUCCESS").last("LIMIT 1"))
                .stream().findFirst().orElse(null);

        RefundOrder r = new RefundOrder();
        r.setId(IdGenerator.nextId());
        r.setOrderId(orderId);
        r.setUserId(userId);
        r.setPaymentNo(pay == null ? null : pay.getPaymentNo());
        r.setType(refundType);
        r.setReason(limit(reason, 200));
        r.setAmount(brief.payAmount());
        r.setStatus("WAIT_APPROVAL");
        r.setPrevStatus(brief.status());
        Instant now = Instant.now();
        r.setCreatedAt(now);
        r.setUpdatedAt(now);
        refunds.insert(r);

        orders.refundState(orderId, new RefundStateReq(r.getPrevStatus(), "REFUND_APPLYING")).unwrap();
        ops.log("REFUND", "[申请] 售后单 " + r.getId() + " 订单 " + orderId + " 金额 ¥" + cen(r.getAmount()) + " 等待运营审批");
        push(r, "REFUND_UPDATE", "您的售后申请已提交，等待运营审批");
        return vo(r);
    }

    public List<Map<String, Object>> list(long userId, int page, int size) {
        List<RefundOrder> rows = refunds.selectList(new LambdaQueryWrapper<RefundOrder>()
                .eq(RefundOrder::getUserId, userId).orderByDesc(RefundOrder::getCreatedAt)
                .last("LIMIT " + (page - 1) * size + "," + Math.min(size, 50)));
        return rows.stream().map(this::vo).toList();
    }

    public Map<String, Object> detail(long userId, long id) {
        return vo(owned(userId, id));
    }

    /** 用户取消：仅 WAIT_APPROVAL；订单回到申请前状态 */
    public boolean cancel(long userId, long id) {
        RefundOrder r = owned(userId, id);
        int rows = refunds.update(null, new LambdaUpdateWrapper<RefundOrder>()
                .eq(RefundOrder::getId, id).eq(RefundOrder::getStatus, "WAIT_APPROVAL")
                .set(RefundOrder::getStatus, "CANCELLED").set(RefundOrder::getUpdatedAt, Instant.now()));
        if (rows == 0) throw new BizException(6104, "售后单已受理，不能取消");
        orders.refundState(r.getOrderId(), new RefundStateReq("REFUND_APPLYING", r.getPrevStatus())).unwrap();
        ops.log("REFUND", "[取消] 售后单 " + id + " 用户撤回申请，订单 " + r.getOrderId() + " → " + r.getPrevStatus());
        return true;
    }

    /* ============================ 运营侧 ============================ */

    public PageResult<Map<String, Object>> adminList(String status, int page, int size) {
        LambdaQueryWrapper<RefundOrder> w = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) w.eq(RefundOrder::getStatus, status);
        w.orderByDesc(RefundOrder::getCreatedAt);
        int total = refunds.selectCount(w).intValue();
        List<Map<String, Object>> records = refunds.selectList(w.last("LIMIT " + (page - 1) * size + "," + size))
                .stream().map(this::vo).toList();
        return new PageResult<>(records, total, page, size);
    }

    /** 审批：pass=true 走渠道退款；pass=false 拒绝并回退订单状态。CAS 防重复审批 */
    public Map<String, Object> approve(long adminId, long id, boolean pass, String remark) {
        RefundOrder r = refunds.selectById(id);
        if (r == null) throw new BizException(6102, "售后单不存在");
        Instant at = Instant.now();
        int rows = refunds.update(null, new LambdaUpdateWrapper<RefundOrder>()
                .eq(RefundOrder::getId, id).eq(RefundOrder::getStatus, "WAIT_APPROVAL")
                .set(RefundOrder::getStatus, pass ? "APPROVED" : "REJECTED")
                .set(RefundOrder::getAuditRemark, limit(remark, 200))
                .set(RefundOrder::getAuditBy, adminId)
                .set(RefundOrder::getAuditAt, at)
                .set(RefundOrder::getUpdatedAt, at));
        if (rows == 0) throw new BizException(6104, "售后单已受理，请刷新后查看");
        r.setStatus(pass ? "APPROVED" : "REJECTED");
        r.setAuditRemark(limit(remark, 200));
        r.setAuditBy(adminId);
        r.setAuditAt(at);

        if (pass) {
            ops.log("REFUND", "[审批] 售后单 " + id + " 审批通过 " + lim(remark) + " → 渠道退款 ¥" + cen(r.getAmount()));
            channelAccept(r);
        } else {
            ops.log("REFUND", "[审批] 售后单 " + id + " 审批拒绝 " + lim(remark) + "，订单回退 " + r.getPrevStatus());
            orders.refundState(r.getOrderId(), new RefundStateReq("REFUND_APPLYING", r.getPrevStatus())).unwrap();
        }
        return vo(r);
    }

    /** 渠道受理：本单无需渠道（积分全额抵扣/0 元单）直接完成；否则 REFUNDING + 沙箱延迟回调 */
    private void channelAccept(RefundOrder r) {
        if (r.getPaymentNo() == null || r.getAmount() == 0) {
            ops.log("REFUND", "[受理] 售后单 " + r.getId() + " 无需渠道退款（流水为空或 0 元），直接完成");
            applyRefundSuccess(r.getId(), r.getPaymentNo(), r.getAmount());
            return;
        }
        int rows = refunds.update(null, new LambdaUpdateWrapper<RefundOrder>()
                .eq(RefundOrder::getId, r.getId()).eq(RefundOrder::getStatus, "APPROVED")
                .set(RefundOrder::getStatus, "REFUNDING")
                .set(RefundOrder::getRefundTradeNo, RF + r.getId())
                .set(RefundOrder::getUpdatedAt, Instant.now()));
        if (rows == 0) return;
        r.setStatus("REFUNDING");
        r.setRefundTradeNo(RF + r.getId());
        push(r, "REFUND_UPDATE", "退款已受理，等待渠道返回");
        // 沙箱：模拟支付渠道异步回调（真实环境由渠道服务器 POST /payments/notify）
        channelSim.schedule(() -> {
            try {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("paymentNo", r.getPaymentNo());
                payload.put("refundId", String.valueOf(r.getId()));
                payload.put("amount", r.getAmount());
                payload.put("tradeStatus", "REFUND_SUCCESS");
                payload.put("sign", signer.refund(r.getPaymentNo(), r.getId(), r.getAmount(), "REFUND_SUCCESS"));
                ops.log("REFUND", "[渠道] 已受理退款单 " + r.getId() + "，回调 REFUND_SUCCESS（沙箱模拟）");
                applyRefundSuccess(r.getId(), r.getPaymentNo(), r.getAmount());
            } catch (Exception e) {
                log.error("sandbox refund callback failed for {}: {}", r.getId(), e.toString());
            }
        }, CHANNEL_DELAY_SEC, TimeUnit.SECONDS);
    }

    private static final String RF = "RF";

    /** 渠道回调成功执行（幂等键 = refund id）：DONE → 回补库存 → 订单 REFUNDED → WS 推送 */
    void applyRefundSuccess(long refundId, String paymentNo, int amount) {
        RefundOrder r = refunds.selectById(refundId);
        if (r == null) throw new BizException(6102, "售后单不存在");
        int rows = refunds.update(null, new LambdaUpdateWrapper<RefundOrder>()
                .eq(RefundOrder::getId, refundId).in(RefundOrder::getStatus, List.of("APPROVED", "REFUNDING"))
                .set(RefundOrder::getStatus, "DONE")
                .set(RefundOrder::getDoneAt, Instant.now())
                .set(RefundOrder::getUpdatedAt, Instant.now()));
        if (rows == 0) {
            ops.log("REFUND", "[回调] 售后单 " + refundId + " 状态非退款中，幂等忽略（当前 " + statusOf(refundId) + "）");
            return;
        }
        if (paymentNo != null) {
            payMapper.update(null, new LambdaUpdateWrapper<Payment>()
                    .eq(Payment::getPaymentNo, paymentNo)
                    .setSql("refunded_amount = refunded_amount + " + amount)
                    .set(Payment::getRefundNo, String.valueOf(refundId))
                    .set(Payment::getUpdatedAt, Instant.now()));
        }
        if (!"EXCHANGE".equals(r.getType())) {
            List<StockItem> items = orders.items(r.getOrderId()).unwrap();
            product.restore(items).unwrap();
        }
        orders.refundState(r.getOrderId(), new RefundStateReq("REFUND_APPLYING", "REFUNDED")).unwrap();
        ops.log("REFUND", "[完成] 售后单 " + refundId + " 订单 " + r.getOrderId() + " → 已退款，库存已回补，金额 ¥" + cen(amount));
        push(r, "REFUND_UPDATE", "退款完成：¥" + cen(amount) + " 已原路退回");
    }

    /** 退款回调分支（由 PayService.notify 委托；验签失败按渠道规范拒绝） */
    public void handleRefundNotify(String paymentNo, Long refundId, int amount, String sign) {
        if (refundId == null) throw new BizException(6003, "验签失败");
        if (!signer.refund(paymentNo, refundId, amount, "REFUND_SUCCESS").equals(sign)) {
            ops.log("REFUND", "[回调] 售后单 " + refundId + " 验签失败，拒绝处理");
            throw new BizException(6003, "验签失败");
        }
        RefundOrder r = refunds.selectById(refundId);
        if (r == null) throw new BizException(6102, "售后单不存在");
        if (!paymentNo.equals(r.getPaymentNo())) throw new BizException(6104, "退款单与原支付流水不匹配");
        if (amount != r.getAmount()) throw new BizException(6004, "退款金额不一致");
        applyRefundSuccess(refundId, paymentNo, amount);
    }

    /** 对账告警（RefundJobs 调用）：REFUNDING 停留超时 */
    public List<RefundOrder> stuckRefunding(Duration olderThan) {
        return refunds.selectList(new LambdaQueryWrapper<RefundOrder>()
                .eq(RefundOrder::getStatus, "REFUNDING")
                .lt(RefundOrder::getUpdatedAt, Instant.now().minus(olderThan)));
    }

    /* ============================ helpers ============================ */

    private RefundOrder owned(long userId, long id) {
        RefundOrder r = refunds.selectById(id);
        if (r == null || !r.getUserId().equals(userId)) throw new BizException(6102, "售后单不存在");
        return r;
    }

    private Map<String, Object> vo(RefundOrder r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("refundId", String.valueOf(r.getId()));
        m.put("orderId", String.valueOf(r.getOrderId()));
        m.put("userId", String.valueOf(r.getUserId()));
        m.put("paymentNo", r.getPaymentNo());
        m.put("type", r.getType());
        m.put("reason", r.getReason());
        m.put("amount", r.getAmount());
        m.put("status", r.getStatus());
        m.put("prevStatus", r.getPrevStatus());
        m.put("auditRemark", r.getAuditRemark());
        m.put("refundTradeNo", r.getRefundTradeNo());
        m.put("doneAt", r.getDoneAt());
        m.put("createdAt", r.getCreatedAt());
        return m;
    }

    private String statusOf(long id) {
        RefundOrder r = refunds.selectById(id);
        return r == null ? "?" : r.getStatus();
    }

    private void push(RefundOrder r, String type, String text) {
        try {
            notifier.send(r.getUserId(), Map.of("type", type,
                    "refundId", String.valueOf(r.getId()),
                    "orderId", String.valueOf(r.getOrderId()),
                    "status", r.getStatus(), "text", text));
        } catch (Exception ignore) {
            // 推送失败不影响状态机
        }
    }

    private static String lim(String s) {
        return limit(s, 60);
    }

    private static String limit(String s, int n) {
        return s == null ? "" : (s.length() > n ? s.substring(0, n) : s);
    }

    private static String cen(int fen) {
        return String.format("%.2f", fen / 100.0);
    }
}
