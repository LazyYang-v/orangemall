package com.orangemall.api.client;

import com.orangemall.api.dto.CouponOpReq;
import com.orangemall.api.dto.GrantCouponReq;
import com.orangemall.api.dto.UserCouponDTO;
import com.orangemall.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@FeignClient(name = "mall-marketing", contextId = "marketingInner", path = "/inner")
public interface MarketingInnerClient {
    /** 用户全部券（先把过期的 UNUSED 置为 EXPIRED） */
    @GetMapping("/coupons/user")
    Result<List<UserCouponDTO>> userCoupons(@RequestParam("userId") Long userId);

    /** UNUSED → LOCKED，否则 4005「优惠券不可用」 */
    @PostMapping("/coupons/lock")
    Result<Boolean> lock(@RequestBody CouponOpReq req);

    /** LOCKED → UNUSED */
    @PostMapping("/coupons/release")
    Result<Boolean> release(@RequestBody CouponOpReq req);

    /** LOCKED → USED */
    @PostMapping("/coupons/use")
    Result<Boolean> use(@RequestBody CouponOpReq req);

    @PostMapping("/coupons/grant")
    Result<UserCouponDTO> grant(@RequestBody GrantCouponReq req);

    /** 首页领券预览：前 limit 个模板（与前端 CouponTemplate 结构一致，含 receivedByMe） */
    @GetMapping("/coupons/templates/preview")
    Result<List<Map<String, Object>>> templatesPreview(@RequestParam("limit") int limit,
                                                       @RequestParam(value = "userId", required = false) Long userId);
}
