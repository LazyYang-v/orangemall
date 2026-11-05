package com.orangemall.marketing.web;

import com.orangemall.common.Result;
import com.orangemall.marketing.dto.Dtos.CouponTemplateVO;
import com.orangemall.marketing.dto.Dtos.CreateTemplateReq;
import com.orangemall.marketing.dto.Dtos.UserCouponVO;
import com.orangemall.marketing.service.CouponService;
import com.orangemall.web.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CouponController {
    private final CouponService coupons;

    /** optional 鉴权：登录后带 receivedByMe */
    @GetMapping("/coupons/templates")
    public Result<List<CouponTemplateVO>> templates() {
        return Result.ok(coupons.templates(UserContext.userId(), Integer.MAX_VALUE));
    }

    @PostMapping("/coupons/templates/{id}/receive")
    public Result<UserCouponVO> receive(@PathVariable("id") String id) {
        return Result.ok(UserCouponVO.of(coupons.receive(UserContext.requireUser(), id)));
    }

    @GetMapping("/coupons/mine")
    public Result<List<UserCouponVO>> mine(@RequestParam(value = "status", required = false) String status) {
        return Result.ok(coupons.userCoupons(UserContext.requireUser(), status).stream().map(UserCouponVO::of).toList());
    }

    @GetMapping("/admin/coupons")
    public Result<List<CouponTemplateVO>> adminList() {
        UserContext.requireAdmin();
        return Result.ok(coupons.allTemplates().stream().map(t -> CouponTemplateVO.of(t, null)).toList());
    }

    @PostMapping("/admin/coupons")
    public Result<CouponTemplateVO> create(@RequestBody(required = false) CreateTemplateReq req) {
        UserContext.requireAdmin();
        return Result.ok(coupons.create(req));
    }
}
