package com.travel.diary;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 旅行日记 · 后端服务入口。
 *
 * @see com.travel.diary.config.WebMvcConfig 认证拦截器与静态资源映射
 * @see com.travel.diary.config.DemoDataInitializer 演示数据
 */
@SpringBootApplication
public class DiaryApplication {

    public static void main(String[] args) {
        SpringApplication.run(DiaryApplication.class, args);
    }
}
