package com.travel.diary.controller;

import com.travel.diary.common.R;
import com.travel.diary.dto.LoginRequest;
import com.travel.diary.dto.RegisterRequest;
import com.travel.diary.security.RequireLogin;
import com.travel.diary.security.UserContext;
import com.travel.diary.service.AuthService;
import com.travel.diary.vo.AuthVO;
import com.travel.diary.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public R<AuthVO> register(@Valid @RequestBody RegisterRequest request) {
        return R.ok(authService.register(request));
    }

    @PostMapping("/login")
    public R<AuthVO> login(@Valid @RequestBody LoginRequest request) {
        return R.ok(authService.login(request));
    }

    /**
     * 登出。
     *
     * <p>JWT 是无状态的,服务端没有会话可销毁 —— 真正的"登出"发生在前端清掉 token。
     * 这个接口存在的意义是让客户端的调用点完整、对称,不出现"注册登录都有接口,
     * 登出却要前端自己偷偷处理"的不一致。
     */
    @PostMapping("/logout")
    @RequireLogin
    public R<Void> logout() {
        return R.ok();
    }

    /** 刷新当前用户信息。前端启动时用它核对本地缓存的 token 是否还有效。 */
    @GetMapping("/me")
    @RequireLogin
    public R<UserVO> me() {
        return R.ok(authService.me(UserContext.requireUserId()));
    }
}
