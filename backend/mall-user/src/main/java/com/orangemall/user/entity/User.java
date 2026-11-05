package com.orangemall.user.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

@Data
@TableName("user")
public class User {
    @TableId
    private Long id;
    private String phone;
    private String passwordHash;
    private String salt;
    private String nickname;
    private String avatar;
    private Integer level;
    private Integer points;
    private Integer creditScore;
    private Boolean blocked;
    private Integer riskHits;
    private String lastAction;
    private String role;
    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
}
