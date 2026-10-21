package com.travel.diary.common;

/**
 * 业务结果码。
 *
 * <p>编码规则:5 位码 = HTTP 语义码 × 100 + 子码。
 * 于是 40003(BAD_CREDENTIALS)一眼能看出属于 400 系列的第 3 个子错误,
 * 前端按前 3 位分类、按后 2 位细分,不必维护一张对照表。
 */
public enum ResultCode {

    OK(0, "ok"),

    /* ---- 400 ---- */
    BAD_REQUEST(40000, "请求参数有误"),
    USERNAME_TAKEN(40001, "这个用户名已经被占用了"),
    EMAIL_TAKEN(40002, "这个邮箱已经注册过了"),
    BAD_CREDENTIALS(40003, "用户名或密码不对"),
    ACCOUNT_DISABLED(40004, "账号已被停用"),
    FILE_TOO_LARGE(40005, "图片太大了,单张请控制在 10MB 以内"),
    UNSUPPORTED_FILE(40006, "只支持 JPG / PNG / WebP 格式的图片"),

    /* ---- 401 / 403 / 404 / 409 ---- */
    UNAUTHORIZED(40100, "请先登录"),
    FORBIDDEN(40300, "没有操作权限"),
    NOT_FOUND(40400, "内容不存在"),
    CONFLICT(40900, "操作冲突,请刷新后重试"),

    /* ---- 500 ---- */
    INTERNAL_ERROR(50000, "服务器开小差了,请稍后重试"),

    /* ---- 503:依赖的外部服务不可用 ---- */
    AI_UNAVAILABLE(50300, "语义搜索暂时不可用,请确认 Ollama 已启动");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }
}
