package com.travel.diary.security;

import com.travel.diary.common.BizException;
import com.travel.diary.common.ResultCode;

/**
 * 当前请求的登录用户。基于 ThreadLocal,必须在请求结束时清理,
 * 否则 Tomcat 线程池复用线程会造成用户身份串号 —— 见 {@link JwtInterceptor#afterCompletion}。
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    /** 未登录时返回 null。用于"登录与否都能访问,但登录后展示更多"的接口。 */
    public static LoginUser get() {
        return HOLDER.get();
    }

    public static Long userIdOrNull() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.id();
    }

    /** 未登录直接抛 401。用于必须登录的接口。 */
    public static LoginUser require() {
        LoginUser user = HOLDER.get();
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return user;
    }

    public static Long requireUserId() {
        return require().id();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
