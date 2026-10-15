package com.travel.diary.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件。
 *
 * <p>没有这个 Bean 时 {@code Page} 查询会静默退化成"查出全部再内存分页" ——
 * 不报错,但数据量一大就会拖垮服务,所以务必保留。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        // 请求页码超出总页数时返回空列表,而不是回到第一页 —— 后者会让翻页出现"鬼打墙"
        pagination.setOverflow(false);
        // 单页上限,兜住恶意的 size=999999
        pagination.setMaxLimit(50L);

        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
