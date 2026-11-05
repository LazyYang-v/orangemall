package com.orangemall.marketing.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

@Data
@TableName("user_coupon")
public class UserCoupon {
    @TableId
    private Long id;
    private Long userId;
    private String templateId;
    private String title;
    private String type;
    private Integer threshold;
    private Integer amount;
    private String scope;
    private Integer scopeRootId;
    /** UNUSED / LOCKED / USED / EXPIRED */
    private String status;
    private Long orderId;
    private Instant validFrom;
    private Instant validTo;
    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
}
