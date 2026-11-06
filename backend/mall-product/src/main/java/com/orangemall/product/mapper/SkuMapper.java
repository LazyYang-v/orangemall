package com.orangemall.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orangemall.product.entity.Sku;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface SkuMapper extends BaseMapper<Sku> {

    /** 条件扣减：WHERE stock >= n 保证不超卖（行锁串行化），影响 0 行即库存不足 */
    @Update("UPDATE sku SET stock = stock - #{n}, sales = sales + #{n} WHERE id = #{id} AND stock >= #{n}")
    int deduct(@Param("id") long id, @Param("n") int n);

    /**
     * 非全局事务写 sku 前先锁行（配合 {@code @GlobalLock}）：Seata 的 SelectForUpdateExecutor 在设保存点后检查全局锁，
     * 被占则回滚到保存点（放掉 InnoDB 行锁）再等待重试。先 UPDATE 再在提交时检查则会一边持有行锁一边等全局锁，
     * 而持锁的全局事务回滚时恰恰要改这一行 → 互等到超时（TC 宕机演练中出现过）。
     */
    @Select("SELECT id FROM sku WHERE id = #{id} FOR UPDATE")
    Long lockById(@Param("id") long id);

    /** 回补库存，销量同步回退（不低于 0） */
    @Update("UPDATE sku SET stock = stock + #{n}, sales = GREATEST(sales - #{n}, 0) WHERE id = #{id}")
    int restore(@Param("id") long id, @Param("n") int n);
}
