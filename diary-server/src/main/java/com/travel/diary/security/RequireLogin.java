package com.travel.diary.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记接口需要登录。加在方法或类上,实际拦截在 {@link JwtInterceptor#preHandle}。
 *
 * <p>用注解而不是路径白名单:白名单散落在配置里,新增接口时容易忘了加上,
 * 结果悄悄变成公开接口;注解就写在这个接口上方,漏不掉。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireLogin {
}
