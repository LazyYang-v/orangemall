package com.orangemall.order.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangemall.api.dto.AddressDTO;
import com.orangemall.order.domain.Logistics;
import com.orangemall.order.domain.OrderItemRow;
import com.orangemall.order.domain.OrderRow;
import com.orangemall.order.domain.OrderVO;
import com.orangemall.order.domain.PriceDetail;
import com.orangemall.order.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** DB 行 ⇄ 前端 VO / JSON 快照 */
@Component
@RequiredArgsConstructor
public class OrderAssembler {
    private final ObjectMapper mapper;
    private final OrderMapper orderMapper;

    /** 地址快照：字段名与前端 Address 完全一致（显式 Map，避免 record 的 isDefault 命名歧义） */
    public String addressJson(AddressDTO a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(a.id()));
        m.put("receiver", a.receiver());
        m.put("phone", a.phone());
        m.put("province", a.province());
        m.put("city", a.city());
        m.put("district", a.district());
        m.put("detail", a.detail());
        m.put("isDefault", a.isDefault());
        return write(m);
    }

    public Map<String, Object> addressMap(AddressDTO a) {
        return read(addressJson(a), new TypeReference<>() {
        });
    }

    public Logistics.Info logistics(String json) {
        return json == null || json.isBlank() ? null : read(json, new TypeReference<>() {
        });
    }

    public String write(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public <T> T read(String json, TypeReference<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private JsonNode tree(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    public static PriceDetail price(OrderRow o) {
        return new PriceDetail(o.getGoodsAmount(), o.getFreight(), o.getFullReduction(), o.getCouponDeduct(),
                o.getPointsDeduct(), o.getSeckillDeduct(), o.getPayAmount());
    }

    public static OrderVO.Item item(OrderItemRow i) {
        return new OrderVO.Item(i.getSkuId(), i.getSpuId(), i.getName(), i.getSpecText(), i.getArt(), i.getTint(),
                i.getPrice(), i.getCount());
    }

    public OrderVO toVO(OrderRow o, List<OrderItemRow> items) {
        return new OrderVO(o.getId(), o.getUserId(), o.getStatus(), o.getSource(),
                items.stream().map(OrderAssembler::item).toList(), price(o), tree(o.getAddressJson()), o.getCouponId(),
                o.getPointsUsed(), o.getRemark(), o.getCreatedAt(), o.getPayDeadline(), o.getPaidAt(), o.getShippedAt(),
                o.getCompletedAt(), o.getClosedAt(), o.getCloseReason(), o.getPayChannel(), o.getPaymentNo(),
                tree(o.getLogisticsJson()));
    }

    public OrderVO toVO(OrderRow o) {
        return toVO(o, orderMapper.findItems(o.getSrc(), List.of(o.getId())));
    }

    /** 批量组装：按来源表分组一次查明细，保持原顺序 */
    public List<OrderVO> toVOs(List<OrderRow> rows) {
        Map<Long, List<OrderItemRow>> items = new LinkedHashMap<>();
        rows.stream().collect(Collectors.groupingBy(OrderRow::getSrc)).forEach((src, list) ->
                orderMapper.findItems(src, list.stream().map(OrderRow::getId).toList())
                        .forEach(i -> items.computeIfAbsent(i.getOrderId(), k -> new ArrayList<>()).add(i)));
        return rows.stream().map(o -> toVO(o, items.getOrDefault(o.getId(), List.of()))).toList();
    }
}
