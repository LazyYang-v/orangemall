package com.orangemall.marketing.web;

import com.orangemall.api.dto.CouponOpReq;
import com.orangemall.api.dto.GrantCouponReq;
import com.orangemall.api.dto.UserCouponDTO;
import com.orangemall.common.Result;
import com.orangemall.marketing.dto.Dtos;
import com.orangemall.marketing.dto.Dtos.CouponTemplateVO;
import com.orangemall.marketing.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 服务间内部接口：实现 com.orangemall.api.client.MarketingInnerClient（网关不路由 /inner/**） */
@RestController
@RequestMapping("/inner/coupons")
@RequiredArgsConstructor
public class InnerCouponController {
    private final CouponService coupons;

    @GetMapping("/user")
    public Result<List<UserCouponDTO>> userCoupons(@RequestParam("userId") Long userId) {
        return Result.ok(coupons.userCoupons(userId, null).stream().map(Dtos::toDto).toList());
    }

    @PostMapping("/lock")
    public Result<Boolean> lock(@RequestBody CouponOpReq req) {
        return Result.ok(coupons.lock(req));
    }

    @PostMapping("/release")
    public Result<Boolean> release(@RequestBody CouponOpReq req) {
        return Result.ok(coupons.release(req));
    }

    @PostMapping("/use")
    public Result<Boolean> use(@RequestBody CouponOpReq req) {
        return Result.ok(coupons.use(req));
    }

    @PostMapping("/grant")
    public Result<UserCouponDTO> grant(@RequestBody GrantCouponReq req) {
        return Result.ok(Dtos.toDto(coupons.grant(req)));
    }

    @GetMapping("/templates/preview")
    public Result<List<CouponTemplateVO>> preview(@RequestParam("limit") int limit,
                                                  @RequestParam(value = "userId", required = false) Long userId) {
        return Result.ok(coupons.templates(userId, Math.max(0, limit)));
    }
}
