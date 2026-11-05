package com.orangemall.web;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.time.Instant;

/** 有数据库的服务才生效：分页插件 + created_at / updated_at 自动填充（实体字段 createdAt/updatedAt，类型 Instant） */
@AutoConfiguration
@ConditionalOnClass(MybatisPlusInterceptor.class)
public class MybatisPlusSupport {

    @Bean
    @ConditionalOnMissingBean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor i = new MybatisPlusInterceptor();
        i.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return i;
    }

    @Bean
    @ConditionalOnMissingBean
    public MetaObjectHandler timeFillHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject m) {
                Instant now = Instant.now();
                strictInsertFill(m, "createdAt", Instant.class, now);
                strictInsertFill(m, "updatedAt", Instant.class, now);
            }

            @Override
            public void updateFill(MetaObject m) {
                setFieldValByName("updatedAt", Instant.now(), m);
            }
        };
    }
}
