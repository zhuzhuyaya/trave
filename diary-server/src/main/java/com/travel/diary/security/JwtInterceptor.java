package com.travel.diary.security;

import com.travel.diary.common.BizException;
import com.travel.diary.common.ResultCode;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 认证拦截器:解析 Authorization 头,填充 {@link UserContext},并按注解读取鉴权要求。
 *
 * <p>刻意不用 Spring Security 过滤器链 —— 本项目只需要"登录了没"和"是不是管理员"两档,
 * 拦截器 + 两个注解即可覆盖,且改得动、看得懂。
 * 密码哈希仍用 spring-security-crypto 的 BCrypt,安全底线不放水。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtInterceptor implements HandlerInterceptor {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 1) 有合法 token 就填充身份;token 缺失或无效只当作"未登录",不在这里报错,
        //    因为多数接口允许匿名访问,是否需要登录由下面的注解决定。
        String token = extractToken(request);
        if (token != null) {
            try {
                UserContext.set(jwtUtil.parse(token));
            } catch (JwtException | IllegalArgumentException e) {
                log.debug("token 无效,按未登录处理:{}", e.getMessage());
                UserContext.clear();
            }
        }

        // 2) 注解鉴权
        if (handler instanceof HandlerMethod handlerMethod) {
            if (hasAnnotation(handlerMethod, RequireAdmin.class)) {
                LoginUser user = UserContext.get();
                if (user == null) {
                    throw new BizException(ResultCode.UNAUTHORIZED);
                }
                if (!user.isAdmin()) {
                    throw new BizException(ResultCode.FORBIDDEN);
                }
            } else if (hasAnnotation(handlerMethod, RequireLogin.class)) {
                if (UserContext.get() == null) {
                    throw new BizException(ResultCode.UNAUTHORIZED);
                }
            }
        }

        return true;
    }

    /**
     * 请求结束必须清理 ThreadLocal。Tomcat 复用线程,不清理会把上一个请求的
     * 用户身份带给下一个请求 —— 这是最容易出、也最难查的鉴权漏洞。
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(PREFIX)) {
            return null;
        }
        String token = header.substring(PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    /** 方法上的注解优先于类上的。 */
    private boolean hasAnnotation(HandlerMethod handlerMethod, Class<? extends java.lang.annotation.Annotation> type) {
        return handlerMethod.getMethodAnnotation(type) != null
                || handlerMethod.getBeanType().isAnnotationPresent(type);
    }
}
