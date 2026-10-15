package com.travel.diary.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "请填写用户名")
    private String username;

    @NotBlank(message = "请填写密码")
    private String password;
}
