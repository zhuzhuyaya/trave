package com.travel.diary.service;

import com.travel.diary.dto.LoginRequest;
import com.travel.diary.dto.RegisterRequest;
import com.travel.diary.entity.User;
import com.travel.diary.vo.AuthVO;
import com.travel.diary.vo.UserVO;

public interface AuthService {

    AuthVO register(RegisterRequest request);

    AuthVO login(LoginRequest request);

    /** 按 id 取用户并转 VO,不存在抛 NOT_FOUND。 */
    UserVO me(Long userId);

    /** 内部复用:取实体,不存在抛 NOT_FOUND。 */
    User requireUser(Long userId);

    UserVO toVO(User user);
}
