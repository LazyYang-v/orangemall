package com.orangemall.product.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/** stock_ledger（V3）：唯一键 (order_id, sku_id, op) 是扣减 / 回补幂等的依据 */
public interface StockLedgerMapper {

    /** 普通 INSERT（不用 INSERT IGNORE：Seata AT 解析器对 IGNORE 支持不完整）；重复记账抛 DuplicateKeyException */
    @Insert("INSERT INTO stock_ledger (order_id, sku_id, op, qty) VALUES (#{orderId}, #{skuId}, #{op}, #{qty})")
    int insert(@Param("orderId") long orderId, @Param("skuId") long skuId, @Param("op") String op, @Param("qty") int qty);

    /** 某订单的流水：sku_id / op / qty */
    @Select("SELECT sku_id AS skuId, op, qty FROM stock_ledger WHERE order_id = #{orderId}")
    List<Map<String, Object>> byOrder(@Param("orderId") long orderId);
}
