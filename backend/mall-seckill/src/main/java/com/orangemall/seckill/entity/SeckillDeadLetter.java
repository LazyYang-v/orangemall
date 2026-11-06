package com.orangemall.seckill.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/** OM-019 秒杀死信留档 */
@Data
@TableName("seckill_dead_letter")
public class SeckillDeadLetter {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String activityId;
    private Long userId;
    /** POISON / INVALID / EXHAUSTED / COMPENSATE_FAILED */
    private String reason;
    private String error;
    private String payload;
    private Integer srcPartition;
    private Long srcOffset;
    private Integer dltPartition;
    private Long dltOffset;
    /** NEW / RESOLVED */
    private String status;
    private String remark;
    private Long resolvedBy;
    private Instant resolvedAt;
    private Instant createdAt;
}
