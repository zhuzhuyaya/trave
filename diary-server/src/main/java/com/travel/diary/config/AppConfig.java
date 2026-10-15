package com.travel.diary.config;

import com.travel.diary.security.JwtUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

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
     * 只用 spring-security-crypto 里这一个工具类,不引入 Security 过滤器链。
     * BCrypt 强度维持默认的 10 轮 —— 再高会明显拖慢登录接口。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
