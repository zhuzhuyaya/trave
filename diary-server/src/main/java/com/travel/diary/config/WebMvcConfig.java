package com.travel.diary.config;

import com.travel.diary.security.JwtInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;
import java.util.Arrays;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;
    private final UploadProperties uploadProperties;

    @Value("${panel.cors.allowed-origins}")
    private String allowedOrigins;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 不做 excludePathPatterns:登录/注册接口本身没有 @RequireLogin,
        // 拦截器只是顺带填充 UserContext,不需要为它们开后门。
        // /uploads/** 不在 /api/** 下,静态图片既不需要身份也不需要 CORS。
        registry.addInterceptor(jwtInterceptor).addPathPatterns("/api/**");
    }

    /**
     * 把上传目录映射成可访问的静态资源。
     *
     * <p>路径统一转成绝对路径再拼 {@code file:} 前缀 —— 相对路径在 Spring 解析时
     * 是相对于 JVM 工作目录的,而 IDE 与命令行启动的工作目录往往不同,
     * 这个差异会导致"命令行能看图、IDEA 里图全裂"的怪现象。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        File dir = new File(uploadProperties.getDir()).getAbsoluteFile();
        registry.addResourceHandler(uploadProperties.getUrlPrefix() + "/**")
                .addResourceLocations("file:" + dir.getPath() + File.separator);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 逐项 trim:yml 里如果写成 "a, b" 带空格,浏览器下发的 Origin 就匹配不上,
        // 而 CORS 失败的报错在浏览器控制台里很难看出是空格引起的。
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);

        registry.addMapping("/api/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                // 用 Bearer token 而非 Cookie,不需要凭据透传。
                // 关掉它同时避免了 allowedOrigins("*") + allowCredentials(true) 的经典冲突。
                .allowCredentials(false)
                .maxAge(3600);
    }
}
