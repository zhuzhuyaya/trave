package com.travel.diary.security;

/**
 * 登录态的最小载体。刻意不带密码、邮箱等敏感字段 ——
 * 它会被塞进 ThreadLocal 并可能被写进日志,只放"我是谁"就够。
 */
public record LoginUser(Long id, String username, String role) {

    public static final String ROLE_ADMIN = "ADMIN";

    public boolean isAdmin() {
        return ROLE_ADMIN.equals(role);
    }
}
