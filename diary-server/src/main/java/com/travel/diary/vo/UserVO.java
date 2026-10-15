package com.travel.diary.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 对外暴露的用户信息。绝不包含 password。 */
@Data
public class UserVO {

    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String bio;
    /** USER / ADMIN */
    private String role;
    private LocalDateTime createdAt;
}
