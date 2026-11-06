package com.orangemall.order.domain;

public final class OrderStatus {
    public static final String PENDING_PAYMENT = "PENDING_PAYMENT";
    public static final String PAID = "PAID";
    public static final String SHIPPED = "SHIPPED";
    public static final String COMPLETED = "COMPLETED";
    public static final String CANCELLED = "CANCELLED";
    public static final String CLOSED = "CLOSED";
    /** 售后只读展示态（docs/refund-design.md §3）：审批中 / 已退款 */
    public static final String REFUND_APPLYING = "REFUND_APPLYING";
    public static final String REFUNDED = "REFUNDED";

    private OrderStatus() {
    }
}
