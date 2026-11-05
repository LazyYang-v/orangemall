package com.orangemall.seckill.support;

/** 秒杀相关 Redis Key */
public final class Keys {
    private Keys() {
    }

    public static final String METRICS = "seckill:metrics";
    public static final String LOCK_ROLL = "seckill:lock:roll";
    public static final String LOCK_CONSISTENCY = "seckill:lock:consistency";

    /** 预热库存（String，DECR） */
    public static String stock(String activityId) {
        return "seckill:stock:" + activityId;
    }

    /** 一人一单已购集合（SET） */
    public static String bought(String activityId) {
        return "seckill:bought:" + activityId;
    }

    /** 已在 Redis 扣减、尚未消费完成的消息数（一致性校验：Redis = DB − 在途） */
    public static String inflight(String activityId) {
        return "seckill:inflight:" + activityId;
    }

    public static String path(String activityId, long userId) {
        return "seckill:path:" + activityId + ":" + userId;
    }

    public static String result(String activityId, long userId) {
        return "seckill:result:" + activityId + ":" + userId;
    }

    public static String bucket(long userId) {
        return "seckill:bucket:" + userId;
    }

    public static String warmLock(String activityId) {
        return "seckill:lock:warm:" + activityId;
    }
}
