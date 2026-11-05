package com.orangemall.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.orangemall.api.client.MarketingInnerClient;
import com.orangemall.api.dto.GrantCouponReq;
import com.orangemall.common.BizException;
import com.orangemall.common.IdGenerator;
import com.orangemall.user.dto.Dtos.PointsGoodsVO;
import com.orangemall.user.dto.Dtos.PointsRecordVO;
import com.orangemall.user.dto.Dtos.PointsVO;
import com.orangemall.user.entity.PointsGoods;
import com.orangemall.user.entity.PointsRecord;
import com.orangemall.user.entity.User;
import com.orangemall.user.mapper.PointsGoodsMapper;
import com.orangemall.user.mapper.PointsRecordMapper;
import com.orangemall.user.mapper.UserMapper;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PointsService {
    private final UserMapper userMapper;
    private final PointsRecordMapper recordMapper;
    private final PointsGoodsMapper goodsMapper;
    private final MarketingInnerClient marketing;

    /**
     * 积分变动 + 写流水（单条 UPDATE 原子增减，扣减带 points >= n 条件防超扣）。
     * 被订单服务通过 /inner/points/change 调用时处于其 Seata 全局事务中（AT 分支）。
     *
     * @return 变动后余额
     */
    @Transactional(rollbackFor = Exception.class)
    public int change(long userId, int change, String reason) {
        if (change < 0) {
            if (userMapper.deductPoints(userId, -change) == 0) {
                if (userMapper.selectById(userId) == null) throw BizException.of(9006, "用户不存在");
                throw BizException.of(8003, "积分不足");
            }
        } else if (change > 0) {
            if (userMapper.addPoints(userId, change) == 0) throw BizException.of(9006, "用户不存在");
        }
        User u = userMapper.selectById(userId);
        if (u == null) throw BizException.of(9006, "用户不存在");
        if (change != 0) addRecord(userId, change, reason);
        return u.getPoints();
    }

    void addRecord(long userId, int change, String reason) {
        PointsRecord r = new PointsRecord();
        r.setId(IdGenerator.nextId());
        r.setUserId(userId);
        r.setChangeAmount(change);
        r.setReason(reason == null ? "" : reason);
        recordMapper.insert(r);
    }

    public PointsVO points(long userId) {
        User u = userMapper.selectById(userId);
        if (u == null) throw BizException.of(401, "登录已过期，请重新登录");
        List<PointsRecordVO> records = recordMapper.selectList(Wrappers.<PointsRecord>lambdaQuery()
                        .eq(PointsRecord::getUserId, userId)
                        .orderByDesc(PointsRecord::getCreatedAt).orderByDesc(PointsRecord::getId))
                .stream().map(PointsRecordVO::of).toList();
        return new PointsVO(u.getPoints(), records);
    }

    public List<PointsGoodsVO> goods() {
        return goodsMapper.selectList(Wrappers.<PointsGoods>lambdaQuery().orderByAsc(PointsGoods::getSort)
                .orderByAsc(PointsGoods::getId)).stream().map(PointsGoodsVO::of).toList();
    }

    /**
     * 积分兑换：扣库存 + 扣积分（本库）+ 发券（营销服务）。
     * 用户服务作为 TM 开启 Seata 全局事务，营销服务发券为 AT 分支：任一步失败整体回滚。
     */
    @GlobalTransactional(name = "points-exchange", rollbackFor = Exception.class)
    @Transactional(rollbackFor = Exception.class)
    public int exchange(long userId, String goodsId) {
        PointsGoods g = goodsMapper.selectById(goodsId);
        if (g == null) throw BizException.of(8001, "商品不存在");
        if (g.getStock() <= 0) throw BizException.of(8002, "已兑完");
        User u = userMapper.selectById(userId);
        if (u == null) throw BizException.of(401, "登录已过期，请重新登录");
        if (u.getPoints() < g.getPoints()) throw BizException.of(8003, "积分不足");
        if (goodsMapper.deductStock(goodsId) == 0) throw BizException.of(8002, "已兑完");
        int balance = change(userId, -g.getPoints(), "兑换「" + g.getName() + "」");
        if (g.getCouponTemplateId() != null) {
            marketing.grant(new GrantCouponReq(userId, g.getCouponTemplateId(), g.getCouponAmount(), g.getCouponTitle())).unwrap();
        }
        return balance;
    }
}
