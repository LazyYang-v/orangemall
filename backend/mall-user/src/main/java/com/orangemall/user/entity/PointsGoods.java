package com.orangemall.user.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("points_goods")
public class PointsGoods {
    @TableId
    private String id;
    private String name;
    private String art;
    private String tint;
    private Integer points;
    private Integer stock;
    /** 兑换后发放的券模板（虚拟商品），NULL 为实物 */
    private String couponTemplateId;
    private Integer couponAmount;
    private String couponTitle;
    private Integer sort;
}
