package com.orangemall.common;

/** 全局常量：请求头、Redis Key 前缀、MQ 名称（服务间共享，修改需同步所有服务） */
public final class Const {
    private Const() {
    }

    /* ---------- 网关透传请求头 ---------- */
    public static final String H_USER_ID = "X-User-Id";
    public static final String H_USER_ROLE = "X-User-Role";
    public static final String H_TRACE = "X-Trace-Id";
    public static final String MDC_TRACE = "traceId";

    /* ---------- Redis ---------- */
    /** token 黑名单：auth:blacklist:{jti}，TTL = token 剩余有效期 */
    public static final String K_TOKEN_BLACKLIST = "auth:blacklist:";
    /** 链路日志 List（LPUSH + LTRIM 200），后台「链路日志」读取 */
    public static final String K_OPS_LOGS = "ops:logs";
    /** WebSocket 推送总线：Redis Pub/Sub 频道，订单服务订阅后推给在线用户 */
    public static final String CH_NOTIFY = "mall:notify";
    /** 信用分 ZSET：member=userId score=信用分（风控、后台按分升序） */
    public static final String K_RISK_CREDIT = "risk:credit";
    /** 黑名单 SET：member=userId */
    public static final String K_RISK_BLOCKED = "risk:blocked";

    /* ---------- RabbitMQ ---------- */
    /** 延迟关单：消息先进 delay 队列（队列级 TTL），到期经 DLX 转入 close 队列 */
    public static final String MQ_ORDER_DELAY_EXCHANGE = "order.delay.exchange";
    public static final String MQ_ORDER_DELAY_30M_QUEUE = "order.delay.30m.queue";
    public static final String MQ_ORDER_DELAY_10M_QUEUE = "order.delay.10m.queue";
    public static final String MQ_ORDER_DELAY_30M_KEY = "order.delay.30m";
    public static final String MQ_ORDER_DELAY_10M_KEY = "order.delay.10m";
    public static final String MQ_ORDER_CLOSE_EXCHANGE = "order.close.exchange";
    public static final String MQ_ORDER_CLOSE_QUEUE = "order.close.queue";
    public static final String MQ_ORDER_CLOSE_KEY = "order.close";
    /** 支付成功：支付服务 → 订单服务 */
    public static final String MQ_PAY_EXCHANGE = "pay.exchange";
    public static final String MQ_PAY_SUCCESS_QUEUE = "order.pay.success.queue";
    public static final String MQ_PAY_SUCCESS_KEY = "pay.success";

    /* ---------- Kafka ---------- */
    public static final String KAFKA_SECKILL_TOPIC = "seckill-order";
}
