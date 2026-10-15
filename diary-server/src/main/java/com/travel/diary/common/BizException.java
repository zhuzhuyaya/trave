package com.travel.diary.common;

import lombok.Getter;

/**
 * 业务异常。被 {@link GlobalExceptionHandler} 捕获后转成 {@link R#fail(int, String)}。
 *
 * <p>刻意继承 {@link RuntimeException} 而非受检异常:业务校验失败是"正常流程的一部分",
 * 不应该逼着每一层签名上都挂 {@code throws}。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(ResultCode resultCode) {
        this(resultCode.code(), resultCode.message());
    }

    public BizException(ResultCode resultCode, String message) {
        this(resultCode.code(), message);
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    /** 内容不存在。{@code what} 传业务名词,如 "日记",拼出 "日记不存在,可能已被删除"。 */
    public static BizException notFound(String what) {
        return new BizException(ResultCode.NOT_FOUND, what + "不存在,可能已被删除");
    }

    public static BizException badRequest(String message) {
        return new BizException(ResultCode.BAD_REQUEST, message);
    }

    public static BizException forbidden(String message) {
        return new BizException(ResultCode.FORBIDDEN, message);
    }
}
