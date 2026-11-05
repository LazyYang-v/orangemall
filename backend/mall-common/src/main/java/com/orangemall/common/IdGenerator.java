package com.orangemall.common;

/**
 * 雪花 ID（63 位）：41 位毫秒时间戳 | 8 位 workerId | 12 位序列 | 2 位基因。
 * <p>
 * 基因位用于订单分表：订单号的基因 = userId &amp; 3，于是 {@code orderId & 3 == userId & 3}，
 * 仅凭订单号即可定位分表 t_order_{n}，无需全表扫描。其它 ID 基因为 0。
 */
public final class IdGenerator {
    private static final long EPOCH = 1704067200000L; // 2024-01-01T00:00:00Z
    private static final long WORKER_BITS = 8, SEQ_BITS = 12, GENE_BITS = 2;
    private static final long MAX_SEQ = (1L << SEQ_BITS) - 1;
    private static final long WORKER_ID;
    private static long lastTs = -1L;
    private static long seq = 0L;

    static {
        String env = System.getenv("WORKER_ID");
        long w;
        try {
            w = env != null ? Long.parseLong(env) : Math.abs((long) java.net.InetAddress.getLocalHost().getHostName().hashCode());
        } catch (Exception e) {
            w = (long) (Math.random() * 255);
        }
        WORKER_ID = w & ((1L << WORKER_BITS) - 1);
    }

    private IdGenerator() {
    }

    public static long nextId() {
        return nextId(0);
    }

    /** @param gene 低 2 位基因（订单传 userId，内部取 &amp;3） */
    public static synchronized long nextId(long gene) {
        long ts = System.currentTimeMillis();
        if (ts < lastTs) ts = lastTs; // 时钟回拨：沿用上次时间戳继续发号
        if (ts == lastTs) {
            seq = (seq + 1) & MAX_SEQ;
            if (seq == 0) {
                while ((ts = System.currentTimeMillis()) <= lastTs) Thread.onSpinWait();
            }
        } else {
            seq = 0;
        }
        lastTs = ts;
        return ((ts - EPOCH) << (WORKER_BITS + SEQ_BITS + GENE_BITS))
                | (WORKER_ID << (SEQ_BITS + GENE_BITS))
                | (seq << GENE_BITS)
                | (gene & 3);
    }

    /** 订单分表下标 */
    public static int shard(long idOrUserId) {
        return (int) (idOrUserId & 3);
    }
}
