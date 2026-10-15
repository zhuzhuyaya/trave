package com.travel.diary.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.travel.diary.dto.UpdateProfileRequest;
import com.travel.diary.entity.User;
import com.travel.diary.mapper.UserMapper;
import com.travel.diary.service.AuthService;
import com.travel.diary.service.ProfileService;
import com.travel.diary.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private final UserMapper userMapper;
    private final AuthService authService;

    @Override
    @Transactional
    public UserVO update(Long userId, UpdateProfileRequest request) {
        authService.requireUser(userId);

        LambdaUpdateWrapper<User> update = new LambdaUpdateWrapper<User>().eq(User::getId, userId);
        boolean dirty = false;

        if (request.getNickname() != null) {
            update.set(User::getNickname, request.getNickname().trim());
            dirty = true;
        }
        if (request.getBio() != null) {
            String bio = request.getBio().trim();
            update.set(User::getBio, bio.isEmpty() ? null : bio);
            dirty = true;
        }
        if (request.getAvatar() != null) {
            String avatar = request.getAvatar().trim();
            update.set(User::getAvatar, avatar.isEmpty() ? null : avatar);
            dirty = true;
        }

        // 一个字段都没传就不发 UPDATE:空更新会把 updated_at 顶到当前时间,
        // 毫无必要地"弄脏"一条没变过的记录。
        if (dirty) {
            userMapper.update(null, update);
        }

        return authService.me(userId);
    }
}
