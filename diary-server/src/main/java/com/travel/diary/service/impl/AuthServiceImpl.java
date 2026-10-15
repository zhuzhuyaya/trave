package com.travel.diary.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.travel.diary.common.BizException;
import com.travel.diary.common.ResultCode;
import com.travel.diary.dto.LoginRequest;
import com.travel.diary.dto.RegisterRequest;
import com.travel.diary.entity.User;
import com.travel.diary.mapper.UserMapper;
import com.travel.diary.security.JwtUtil;
import com.travel.diary.service.AuthService;
import com.travel.diary.vo.AuthVO;
import com.travel.diary.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Override
    @Transactional
    public AuthVO register(RegisterRequest request) {
        // 先查一次是为了给出**具体**的提示("这个用户名已经被占用了"),
        // 而不是为了正确性 —— 两个请求同时注册同一个名字时,两边都查不到,
        // 都由唯一索引拦下,由 GlobalExceptionHandler 转成 CONFLICT。
        // 常规路径靠这次预查拿到更好的文案。
        String username = request.getUsername().trim();
        String email = request.getEmail().trim().toLowerCase();

        if (userMapper.exists(new LambdaQueryWrapper<User>().eq(User::getUsername, username))) {
            throw new BizException(ResultCode.USERNAME_TAKEN);
        }
        if (userMapper.exists(new LambdaQueryWrapper<User>().eq(User::getEmail, email))) {
            throw new BizException(ResultCode.EMAIL_TAKEN);
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setNickname(request.getNickname().trim());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(User.ROLE_USER);
        user.setStatus(User.STATUS_ACTIVE);
        userMapper.insert(user);

        return issue(user);
    }

    @Override
    public AuthVO login(LoginRequest request) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername().trim()));

        // 不区分"用户不存在"与"密码不对" —— 区分开等于白送一个用户名枚举接口。
        // 两条路径合并成同一句提示,顺带让它们的响应耗时接近。
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BizException(ResultCode.BAD_CREDENTIALS);
        }
        if (!user.isActive()) {
            throw new BizException(ResultCode.ACCOUNT_DISABLED);
        }

        return issue(user);
    }

    @Override
    public UserVO me(Long userId) {
        return toVO(requireUser(userId));
    }

    @Override
    public User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.notFound("用户");
        }
        return user;
    }

    @Override
    public UserVO toVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setBio(user.getBio());
        vo.setRole(user.getRole());
        vo.setCreatedAt(user.getCreatedAt());
        return vo;
    }

    /** 签发 token。角色写进 claim,鉴权时无需回查数据库。 */
    private AuthVO issue(User user) {
        AuthVO auth = new AuthVO();
        auth.setToken(jwtUtil.generate(user.getId(), user.getUsername(), user.getRole()));
        auth.setUser(toVO(user));
        return auth;
    }
}
