package com.travel.diary.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记接口需要管理员。未登录返回 401,已登录但角色不足返回 403。
 *
 * <p>本项目的业务里没有"只有管理员能做"的操作,这个注解是留给将来的运营后台
 * 与数据修复入口的 —— 鉴权层先就位,免得日后加。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireAdmin {
}
