package com.orangemall.seckill.support;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 场次与商品模板：与前端 Mock（frontend/src/mock/db.ts seckillSessions / seckillItems / warmUp）逐项对齐。
 * 每 2 小时一场（Asia/Shanghai），窗口为当前场次的前 1 场到后 2 场；不同场次按 offset 轮换商品顺序。
 */
public final class SeckillCalendar {
    private SeckillCalendar() {
    }

    public static final long BLOCK = 2 * 3600_000L;
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter ISO_MS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH").withZone(ZONE);

    /** 秒杀模板：spuId、skuId（该 SPU 的第一个 SKU）、秒杀价、库存 */
    public record Tpl(long spuId, long skuId, int price, int stock) {
    }

    public static final List<Tpl> TEMPLATE = List.of(
            new Tpl(1002, 100201, 19900, 100),
            new Tpl(1003, 100301, 59900, 50),
            new Tpl(1004, 100401, 26900, 80),
            new Tpl(1005, 100501, 7900, 200),
            new Tpl(1001, 100101, 699900, 10),
            new Tpl(1007, 100701, 99900, 30),
            new Tpl(1015, 101501, 3990, 500),
            new Tpl(1013, 101301, 29900, 60));

    public record Session(String id, long start, long end) {
        public String status(long now) {
            return now < start ? "UPCOMING" : now >= end ? "ENDED" : "ONGOING";
        }

        public String label() {
            return HOUR.format(Instant.ofEpochMilli(start)) + ":00";
        }

        public String startIso() {
            return iso(start);
        }

        public String endIso() {
            return iso(end);
        }
    }

    public static String iso(long ms) {
        return ISO_MS.format(Instant.ofEpochMilli(ms));
    }

    public static long currentBlockStart(long now) {
        long tz = ZONE.getRules().getOffset(Instant.ofEpochMilli(now)).getTotalSeconds() * 1000L;
        return Math.floorDiv(now + tz, BLOCK) * BLOCK - tz;
    }

    /** 窗口内 4 个场次：k = -1, 0, 1, 2 */
    public static List<Session> window(long now) {
        long cur = currentBlockStart(now);
        List<Session> list = new ArrayList<>(4);
        for (int k = -1; k <= 2; k++) {
            long start = cur + k * BLOCK;
            list.add(new Session("S" + start, start, start + BLOCK));
        }
        return list;
    }

    public static long sessionStart(String sessionId) {
        return Long.parseLong(sessionId.substring(1));
    }

    /** 场次内商品顺序（与 Mock 相同的轮换规则） */
    public static List<Tpl> rotated(long sessionStart) {
        long offset = sessionStart / BLOCK;
        int n = TEMPLATE.size();
        List<Tpl> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) list.add(TEMPLATE.get((int) Math.floorMod(i + offset, n)));
        return list;
    }

    public static String activityId(String sessionId, long skuId) {
        return sessionId + "-" + skuId;
    }

    /** 自动预热时用确定性伪随机生成「已抢比例」，已结束场次视为售罄（同 Mock warmUp） */
    public static int warmSold(String activityId, int stock, boolean ended) {
        if (ended) return stock;
        int seed = 0;
        for (int i = 0; i < activityId.length(); i++) seed += activityId.charAt(i);
        return (int) Math.floor(stock * (0.35 + (seed % 40) / 100.0));
    }
}
