package com.travel.diary.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.validation.ConstraintViolationException;

import java.util.List;

/**
 * 全局异常处理。
 *
 * <p>一律返回 {@link R} 且 HTTP 状态码恒为 200 —— 与成功响应保持同一种形状,
 * 前端就只有一个解析分支。真要说缺点,是浏览器 DevTools 的 Network 面板看不出红,
 * 但业务码在 body 里写得很清楚,调试并没有变难。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常:message 是给用户看的,直接透出。 */
    @ExceptionHandler(BizException.class)
    public R<Void> handleBiz(BizException e) {
        return R.fail(e.getCode(), e.getMessage());
    }

    /** {@code @RequestBody} 上的 {@code @Valid} 失败。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleBodyInvalid(MethodArgumentNotValidException e) {
        return R.fail(ResultCode.BAD_REQUEST, firstFieldMessage(e.getBindingResult().getFieldErrors()));
    }

    /** 表单 / query 参数绑定失败。 */
    @ExceptionHandler(BindException.class)
    public R<Void> handleBind(BindException e) {
        return R.fail(ResultCode.BAD_REQUEST, firstFieldMessage(e.getBindingResult().getFieldErrors()));
    }

    /** {@code @Validated} 加在类上时,方法参数校验失败走这里。 */
    @ExceptionHandler(ConstraintViolationException.class)
    public R<Void> handleConstraint(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .findFirst()
                .map(violation -> violation.getMessage())
                .orElse(ResultCode.BAD_REQUEST.message());
        return R.fail(ResultCode.BAD_REQUEST, message);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public R<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return R.fail(ResultCode.BAD_REQUEST, "缺少参数:" + e.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public R<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return R.fail(ResultCode.BAD_REQUEST, "参数格式不对:" + e.getName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public R<Void> handleUnreadable(HttpMessageNotReadableException e) {
        return R.fail(ResultCode.BAD_REQUEST, "请求体格式不对,应为合法 JSON");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public R<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return R.fail(ResultCode.BAD_REQUEST, "不支持 " + e.getMethod() + " 请求");
    }

    /** 路径没有匹配到任何 Controller,也没有命中静态资源。 */
    @ExceptionHandler(NoResourceFoundException.class)
    public R<Void> handleNoResource(NoResourceFoundException e) {
        return R.fail(ResultCode.NOT_FOUND, "接口不存在:" + e.getResourcePath());
    }

    /** 唯一索引冲突。多半是并发重复提交,提示用户重试而不是报 500。 */
    @ExceptionHandler(DuplicateKeyException.class)
    public R<Void> handleDuplicateKey(DuplicateKeyException e) {
        log.warn("唯一索引冲突:{}", e.getMostSpecificCause().getMessage());
        return R.fail(ResultCode.CONFLICT, "这条记录已经存在了,请勿重复提交");
    }

    /** 超过 spring.servlet.multipart 上限时由容器抛出,业务码转成更具体的 FILE_TOO_LARGE。 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public R<Void> handleMaxUpload(MaxUploadSizeExceededException e) {
        return R.fail(ResultCode.FILE_TOO_LARGE);
    }

    /** 兜底。日志带方法 + URI,方便回查是哪一次请求炸的;给用户的只有一句人话。 */
    @ExceptionHandler(Exception.class)
    public R<Void> handleUnexpected(Exception e, jakarta.servlet.http.HttpServletRequest request) {
        log.error("未处理异常 {} {}", request.getMethod(), request.getRequestURI(), e);
        return R.fail(ResultCode.INTERNAL_ERROR);
    }

    /**
     * 只透出第一个字段错误。
     *
     * <p>表单一次性弹 5 条校验提示并不好读,而且用户改一个字段就重提一次,
     * 逐条浮现的节奏反而更接近原生表单的体验。
     */
    private String firstFieldMessage(List<FieldError> errors) {
        if (errors.isEmpty()) {
            return ResultCode.BAD_REQUEST.message();
        }
        FieldError first = errors.get(0);
        String message = first.getDefaultMessage();
        return message == null || message.isBlank() ? ResultCode.BAD_REQUEST.message() : message;
    }
}
