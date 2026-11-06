package com.orangemall.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orangemall.seckill.entity.SeckillDeadLetter;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface SeckillDeadLetterMapper extends BaseMapper<SeckillDeadLetter> {

    /** 幂等落库：同一 DLT 位点重复投递只记一次（返回 1 = 新增，0 = 已存在） */
    @Insert("INSERT IGNORE INTO seckill_dead_letter (activity_id, user_id, reason, error, payload, src_partition, src_offset, "
            + "dlt_partition, dlt_offset, status) VALUES (#{d.activityId}, #{d.userId}, #{d.reason}, #{d.error}, #{d.payload}, "
            + "#{d.srcPartition}, #{d.srcOffset}, #{d.dltPartition}, #{d.dltOffset}, 'NEW')")
    int insertIgnore(@Param("d") SeckillDeadLetter d);

    @Select("<script>SELECT * FROM seckill_dead_letter <where><if test='status != null'>status = #{status}</if></where> "
            + "ORDER BY id DESC LIMIT #{limit}</script>")
    List<SeckillDeadLetter> recent(@Param("status") String status, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM seckill_dead_letter WHERE status = 'NEW'")
    int countNew();

    @Select("SELECT COUNT(*) FROM seckill_dead_letter")
    int countAll();

    @Update("UPDATE seckill_dead_letter SET status = 'RESOLVED', remark = #{remark}, resolved_by = #{by}, resolved_at = NOW(3) "
            + "WHERE id = #{id} AND status = 'NEW'")
    int resolve(@Param("id") long id, @Param("remark") String remark, @Param("by") long by);
}
