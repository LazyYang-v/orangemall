package com.orangemall.user.web;

import com.orangemall.common.Result;
import com.orangemall.user.dto.Dtos.BalanceVO;
import com.orangemall.user.dto.Dtos.PointsGoodsVO;
import com.orangemall.user.dto.Dtos.PointsVO;
import com.orangemall.user.service.PointsService;
import com.orangemall.web.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/points")
@RequiredArgsConstructor
public class PointsController {
    private final PointsService points;

    @GetMapping
    public Result<PointsVO> points() {
        return Result.ok(points.points(UserContext.requireUser()));
    }

    @GetMapping("/goods")
    public Result<List<PointsGoodsVO>> goods() {
        return Result.ok(points.goods());
    }

    @PostMapping("/goods/{id}/exchange")
    public Result<BalanceVO> exchange(@PathVariable("id") String id) {
        return Result.ok(new BalanceVO(points.exchange(UserContext.requireUser(), id)));
    }
}
