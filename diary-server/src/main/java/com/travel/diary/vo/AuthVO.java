package com.travel.diary.vo;

import lombok.Data;

/** 注册 / 登录成功后的响应。 */
@Data
public class AuthVO {

    private String token;

    private UserVO user;
}
