package com.orangemall.marketing.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

@Data
@TableName("coupon_template")
public class CouponTemplate {
    @TableId
    private String id;
    private String title;
    private String type;
    private Integer threshold;
    private Integer amount;
    private String scope;
    /** 适用根类目，NULL = 全场 */
    private Integer scopeRootId;
    private Integer total;
    private Integer received;
    private Integer validDays;
    private Integer perUserLimit;
    private Instant startAt;
    private Instant endAt;
    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
}
