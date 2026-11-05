package com.orangemall.order.domain;

public final class OrderStatus {
    public static final String PENDING_PAYMENT = "PENDING_PAYMENT";
    public static final String PAID = "PAID";
    public static final String SHIPPED = "SHIPPED";
    public static final String COMPLETED = "COMPLETED";
    public static final String CANCELLED = "CANCELLED";
    public static final String CLOSED = "CLOSED";

    private OrderStatus() {
    }
}
