package com.travel.diary;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 旅行日记 — 后端服务。
 *
 * <p>启动前请设置数据库密码:
 * <pre>DB_PASSWORD=你的密码 mvn spring-boot:run</pre>
 *
 * <p>Spring AI 的自动配置通过 application.yml 的
 * {@code spring.ai.model.chat/embedding: none} 整体静默关闭( setter 绑定 relay,
 * 写 none 时两个 starter 的自动配置条件不满足,不打日志也不报错)——
 * DeepSeek starter 在 key 缺失时是"启动即炸"而不是"条件不满足"。
 * AI 模型 Bean 改由 {@link com.travel.diary.config.AiConfig} 按
 * {@code panel.ai.enabled} 手动装配:没配 key / 没起 Ollama 时日记功能照常,
 * 只有 AI 入口提示"未启用"。
 */
@SpringBootApplication
@MapperScan("com.travel.diary.mapper")
public class DiaryApplication {

    public static void main(String[] args) {
        SpringApplication.run(DiaryApplication.class, args);
    }
}
