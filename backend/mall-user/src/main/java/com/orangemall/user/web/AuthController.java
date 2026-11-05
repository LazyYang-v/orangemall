package com.orangemall.user.web;

import com.orangemall.common.Result;
import com.orangemall.user.dto.Dtos.LoginReq;
import com.orangemall.user.dto.Dtos.LoginResp;
import com.orangemall.user.dto.Dtos.RegisterReq;
import com.orangemall.user.dto.Dtos.SmsReq;
import com.orangemall.user.dto.Dtos.SmsResp;
import com.orangemall.user.entity.User;
import com.orangemall.user.service.AuthService;
import com.orangemall.user.service.RiskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService auth;
    private final RiskService risk;

    @PostMapping("/login")
    public Result<LoginResp> login(@RequestBody LoginReq req) {
        return Result.ok(auth.login(req));
    }

    @PostMapping("/register")
    public Result<LoginResp> register(@RequestBody RegisterReq req) {
        User u = auth.register(req); // Seata 全局事务已提交
        risk.syncCredit(u.getId(), u.getCreditScore());
        return Result.ok(auth.issue(u));
    }

    @PostMapping("/sms")
    public Result<SmsResp> sms(@RequestBody(required = false) SmsReq req) {
        return Result.ok(auth.sms(req == null ? null : req.phone()));
    }

    @PostMapping("/logout")
    public Result<Boolean> logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        auth.logout(authorization);
        return Result.ok();
    }
}
