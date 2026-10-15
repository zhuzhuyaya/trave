package com.travel.diary.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("users")
public class User {

    public static final String ROLE_USER = "USER";
    public static final String ROLE_ADMIN = "ADMIN";

    public static final int STATUS_ACTIVE = 1;
    public static final int STATUS_DISABLED = 0;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String email;

    /** BCrypt 哈希。加 @JsonIgnore 兜底 —— 任何路径下都不会被序列化出去。 */
    @JsonIgnore
    private String password;

    private String nickname;

    private String avatar;

    private String bio;

    /** USER / ADMIN */
    private String role;

    /** 1 正常 / 0 停用 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public boolean isAdmin() {
        return ROLE_ADMIN.equals(role);
    }

    public boolean isActive() {
        return status == null || status == STATUS_ACTIVE;
    }
}
