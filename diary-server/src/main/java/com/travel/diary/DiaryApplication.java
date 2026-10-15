package com.travel.diary;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 旅行日记 — 后端服务。
 *
 * <p>启动前请设置数据库密码:
 * <pre>DB_PASSWORD=你的密码 mvn spring-boot:run</pre>
 */
@SpringBootApplication
@MapperScan("com.travel.diary.mapper")
public class DiaryApplication {

    public static void main(String[] args) {
        SpringApplication.run(DiaryApplication.class, args);
    }
}
