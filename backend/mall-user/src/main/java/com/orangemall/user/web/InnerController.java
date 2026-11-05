package com.orangemall.user.web;

import com.orangemall.api.dto.AddressDTO;
import com.orangemall.api.dto.PointsChangeReq;
import com.orangemall.api.dto.UserBrief;
import com.orangemall.common.BizException;
import com.orangemall.common.Result;
import com.orangemall.user.entity.User;
import com.orangemall.user.mapper.UserMapper;
import com.orangemall.user.service.AddressService;
import com.orangemall.user.service.PointsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 服务间内部接口：实现 com.orangemall.api.client.UserInnerClient（网关不路由 /inner/**） */
@RestController
@RequestMapping("/inner")
@RequiredArgsConstructor
public class InnerController {
    private final UserMapper userMapper;
    private final AddressService addresses;
    private final PointsService points;

    @GetMapping("/users/{id}")
    public Result<UserBrief> user(@PathVariable("id") Long id) {
        User u = userMapper.selectById(id);
        if (u == null) throw BizException.of(9006, "用户不存在");
        return Result.ok(new UserBrief(u.getId(), u.getNickname(), u.getPhone(), u.getPoints(), u.getCreditScore(),
                Boolean.TRUE.equals(u.getBlocked()), u.getRole()));
    }

    @GetMapping("/users/{id}/addresses")
    public Result<List<AddressDTO>> addresses(@PathVariable("id") Long id) {
        return Result.ok(addresses.listDefaultFirst(id).stream()
                .map(a -> new AddressDTO(a.getId(), a.getReceiver(), a.getPhone(), a.getProvince(), a.getCity(),
                        a.getDistrict(), a.getDetail(), Boolean.TRUE.equals(a.getIsDefault())))
                .toList());
    }

    @PostMapping("/points/change")
    public Result<Integer> changePoints(@RequestBody PointsChangeReq req) {
        if (req == null || req.userId() == null) throw BizException.of(400, "请求参数错误");
        return Result.ok(points.change(req.userId(), req.change(), req.reason()));
    }
}
