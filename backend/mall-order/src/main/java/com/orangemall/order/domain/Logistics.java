package com.orangemall.order.domain;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** 物流信息（logistics_json），与前端 LogisticsInfo / LogisticsTrace 一致；traces 新的在前 */
public final class Logistics {
    private static final DateTimeFormatter ISO_MS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    public record Trace(String time, String state, String desc, String location) {
    }

    public record Info(String company, String trackingNo, String state, List<Trace> traces) {
        /** 新轨迹插到最前，状态随之推进 */
        public Info prepend(Trace t) {
            List<Trace> list = new ArrayList<>(traces == null ? List.of() : traces);
            list.add(0, t);
            return new Info(company, trackingNo, t.state(), list);
        }
    }

    /** 模拟承运商的轨迹推进步骤（与 Mock 的 LOGISTICS_STEPS 一致） */
    public static final List<Trace> STEPS = List.of(
            new Trace(null, "IN_TRANSIT", "快件已从【西安集散中心】发出，下一站【汉中转运中心】", "西安市"),
            new Trace(null, "IN_TRANSIT", "快件已到达【汉中转运中心】", "汉中市"),
            new Trace(null, "DELIVERING", "快递员【李师傅 139****8866】正在为您派件", "汉中市"),
            new Trace(null, "SIGNED", "已签收，签收人：本人。如有疑问请联系快递员", "汉中市"));

    private Logistics() {
    }

    public static String iso(Instant t) {
        return ISO_MS.format(t);
    }
}
