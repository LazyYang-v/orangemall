package com.orangemall.api.client;

import com.orangemall.api.dto.PaidPayment;
import com.orangemall.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/** 支付服务内部接口：对账数据源（paid 集合） */
@FeignClient(name = "mall-pay", contextId = "payInner", path = "/inner")
public interface PayInnerClient {

    /** paidAt > from 的支付成功流水（状态 SUCCESS），按时间升序，至多 500 条 */
    @GetMapping("/pay/paid")
    Result<List<PaidPayment>> paidSince(@RequestParam("from") long fromEpochSec);
}
