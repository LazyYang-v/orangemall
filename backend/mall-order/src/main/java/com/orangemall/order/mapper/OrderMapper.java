package com.orangemall.order.mapper;

import com.orangemall.order.domain.OrderItemRow;
import com.orangemall.order.domain.OrderRow;
import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/** 分表 Mapper（SQL 见 resources/mapper/OrderMapper.xml），参数 s = 表后缀 0..3 / archive */
@Mapper
public interface OrderMapper {

    @Data
    class StatusCount {
        private String status;
        private int cnt;
    }

    @Data
    class DailyStat {
        private String day;
        private String status;
        private int cnt;
        private long amount;
    }

    /** 列表查询条件；keyword 已转为 LIKE 模式 */
    @Data
    class ListQuery {
        private List<String> tables;
        private Long userId;
        private List<String> statuses;
        private String source;
        private String keyword;
        private boolean adminKeyword;
        /** 管理端关键词分流（OM-015）：ID=主键精确 / PHONE=手机号前缀 / RECEIVER=收货人前缀，均可走索引 */
        private String keywordMode;
        private Long keywordId;
        private int offset;
        private int size;
    }

    int insertOrder(@Param("s") String s, @Param("o") OrderRow o);

    int insertItem(@Param("s") String s, @Param("i") OrderItemRow i);

    OrderRow findOrder(@Param("s") String s, @Param("id") long id);

    List<OrderItemRow> findItems(@Param("s") String s, @Param("ids") Collection<Long> ids);

    int closeIfPending(@Param("s") String s, @Param("id") long id, @Param("status") String status,
                       @Param("now") Instant now, @Param("reason") String reason);

    int markPaid(@Param("s") String s, @Param("id") long id, @Param("paidAt") Instant paidAt,
                 @Param("channel") String channel, @Param("paymentNo") String paymentNo);

    int ship(@Param("s") String s, @Param("id") long id, @Param("now") Instant now, @Param("logistics") String logistics);

    int casLogistics(@Param("s") String s, @Param("id") long id, @Param("expect") String expect,
                     @Param("logistics") String logistics);

    int complete(@Param("s") String s, @Param("id") long id, @Param("now") Instant now, @Param("logistics") String logistics);

    int expireNow(@Param("s") String s, @Param("id") long id, @Param("deadline") Instant deadline);

    /** 售后状态流转（from 为空 = 无条件，调用方先校验合法性）；返回 affected rows */
    int refundState(@Param("s") String s, @Param("id") long id, @Param("from") String from, @Param("to") String to,
                    @Param("now") Instant now);

    List<OrderRow> list(@Param("q") ListQuery q);

    int count(@Param("q") ListQuery q);

    List<StatusCount> statusCounts(@Param("tables") List<String> tables, @Param("userId") Long userId);

    List<DailyStat> dailyStats(@Param("tables") List<String> tables, @Param("from") Instant from);

    List<OrderRow> overduePending(@Param("s") String s, @Param("before") Instant before, @Param("limit") int limit);

    /** 库存对账：[from, to) 内关闭 / 取消的普通单（idx_closed_at） */
    List<Long> closedNormalBetween(@Param("s") String s, @Param("from") Instant from, @Param("to") Instant to, @Param("limit") int limit);

    /** 库存对账：[from, to) 内创建的普通单（idx_created_at） */
    List<Long> createdNormalBetween(@Param("s") String s, @Param("from") Instant from, @Param("to") Instant to, @Param("limit") int limit);

    List<OrderRow> shippedInTransit(@Param("s") String s, @Param("limit") int limit);

    List<Long> archivableIds(@Param("s") String s, @Param("before") Instant before, @Param("limit") int limit);

    int copyToArchive(@Param("s") String s, @Param("ids") Collection<Long> ids);

    int copyItemsToArchive(@Param("s") String s, @Param("ids") Collection<Long> ids);

    int deleteOrders(@Param("s") String s, @Param("ids") Collection<Long> ids);

    int deleteItems(@Param("s") String s, @Param("ids") Collection<Long> ids);
}
