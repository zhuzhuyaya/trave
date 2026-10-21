package com.travel.diary.config;

import com.travel.diary.security.JwtUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * 通用 Bean。
 */
@Configuration
public class AppConfig {

    @Bean
    public JwtUtil jwtUtil(JwtProperties properties) {
        return new JwtUtil(properties.getSecret(), properties.getExpireHours());
    }

    /**
     * 瓦片回源专用 HTTP 客户端:连接池常驻、按域名白名单分流,
     * 与业务侧互不抢连接;不follow重定向,上游拒绝就是真拒绝。
     */
    @Bean
    public HttpClient tileHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                // 四个子域各自一条 HTTP 连接(对端是 HTTP/1.1),
                // 由 java.net.http 内部的连接池复用,避免每次回源都加一次 TCP+TLS 的 1 秒
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * 只用 spring-security-crypto 里这一个工具类,不引入 Security 过滤器链。
     * BCrypt 强度维持默认的 10 轮 —— 再高会明显拖慢登录接口。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
