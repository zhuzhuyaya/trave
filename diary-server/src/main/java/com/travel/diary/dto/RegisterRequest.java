package com.travel.diary.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    /**
     * 用户名限定为字母/数字/下划线:它同时是登录名与个人主页 URL 的一部分,
     * 放开中文和空格会让 @PathVariable 与日志都变得难处理。
     */
    @NotBlank(message = "请填写用户名")
    @Size(min = 3, max = 20, message = "用户名需在 3-20 个字符之间")
    @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
    private String username;

    @NotBlank(message = "请填写邮箱")
    @Email(message = "邮箱格式不对")
    @Size(max = 128, message = "邮箱太长了")
    private String email;

    @NotBlank(message = "请填写密码")
    @Size(min = 6, max = 64, message = "密码需在 6-64 位之间")
    private String password;

    @NotBlank(message = "请填写昵称")
    @Size(min = 1, max = 20, message = "昵称需在 1-20 个字之间")
    private String nickname;
}
