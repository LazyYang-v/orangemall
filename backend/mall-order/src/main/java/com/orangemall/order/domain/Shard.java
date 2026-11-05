package com.orangemall.order.domain;

import com.orangemall.common.IdGenerator;

import java.util.List;

/**
 * 分表路由：订单号低 2 位基因 = userId &amp; 3，于是 t_order_{orderId &amp; 3} 与 t_order_{userId &amp; 3} 是同一张表。
 * 表后缀只会是 "0".."3" / "archive"（代码常量，非用户输入），在 Mapper 中用 ${s} 拼接表名。
 */
public final class Shard {
    public static final String ARCHIVE = "archive";
    public static final List<String> SHARDS = List.of("0", "1", "2", "3");
    /** 后台全量查询：4 张分表 + 归档表 */
    public static final List<String> ALL = List.of("0", "1", "2", "3", ARCHIVE);

    private Shard() {
    }

    public static String of(long orderIdOrUserId) {
        return String.valueOf(IdGenerator.shard(orderIdOrUserId));
    }
}
