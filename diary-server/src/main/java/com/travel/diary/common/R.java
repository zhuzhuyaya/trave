package com.travel.diary.common;

import lombok.Getter;

/**
 * 统一响应体:{@code { code, message, data }}。
 *
 * <p>{@code code == 0} 表示成功,其余为业务错误,具体含义见 {@link ResultCode}。
 * HTTP 状态码恒为 200 —— 业务结果只走 {@code code},前端因此只需要一个判断分支。
 */
@Getter
public class R<T> {

    private final int code;
    private final String message;
    private final T data;

    private R(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> R<T> ok(T data) {
        return new R<>(ResultCode.OK.code(), ResultCode.OK.message(), data);
    }

    public static R<Void> ok() {
        return new R<>(ResultCode.OK.code(), ResultCode.OK.message(), null);
    }

    public static <T> R<T> fail(ResultCode resultCode) {
        return new R<>(resultCode.code(), resultCode.message(), null);
    }

    public static <T> R<T> fail(ResultCode resultCode, String message) {
        return new R<>(resultCode.code(), message, null);
    }

    public static <T> R<T> fail(int code, String message) {
        return new R<>(code, message, null);
    }
}
