package com.travel.diary.service;

import com.travel.diary.dto.UpdateProfileRequest;
import com.travel.diary.vo.UserVO;

public interface ProfileService {

    /** 更新昵称 / 签名 / 头像。只动客户端真的传了的字段。 */
    UserVO update(Long userId, UpdateProfileRequest request);
}
