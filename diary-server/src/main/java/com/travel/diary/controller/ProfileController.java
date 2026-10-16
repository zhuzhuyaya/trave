package com.travel.diary.controller;

import com.travel.diary.common.R;
import com.travel.diary.dto.UpdateProfileRequest;
import com.travel.diary.security.RequireLogin;
import com.travel.diary.security.UserContext;
import com.travel.diary.service.AuthService;
import com.travel.diary.service.ProfileService;
import com.travel.diary.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 个人资料。
 *
 * <p>单独一个 Controller 而不是塞进 {@code AuthController}:认证是"你是谁",
 * 资料是"你长什么样",前者在登录前后都可能被匿名调用,后者的入口永远需要登录。
 */
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;
    private final AuthService authService;

    @GetMapping
    @RequireLogin
    public R<UserVO> me() {
        return R.ok(authService.me(UserContext.requireUserId()));
    }

    @PutMapping
    @RequireLogin
    public R<UserVO> update(@Valid @RequestBody UpdateProfileRequest request) {
        return R.ok(profileService.update(UserContext.requireUserId(), request));
    }
}
