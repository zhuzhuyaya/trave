package com.travel.diary.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 签发与校验。
 *
 * <p>基于 jjwt 0.12.x。注意 0.12 相对 0.11 是破坏性变更:
 * {@code setSubject} → {@code subject}、{@code SignatureAlgorithm.HS256} 已废弃、
 * 解析要用 {@code Jwts.parser().verifyWith(key)}。照抄旧教程会编译不过。
 */
public class JwtUtil {

    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLE = "role";

    private final SecretKey key;
    private final long expireMillis;

    /**
     * @param secret      HS256 要求密钥不短于 32 字节,否则 jjwt 抛 WeakKeyException
     * @param expireHours token 有效期(小时)
     */
    public JwtUtil(String secret, long expireHours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMillis = expireHours * 60L * 60L * 1000L;
    }

    public String generate(Long userId, String username, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_ROLE, role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMillis))
                .signWith(key)
                .compact();
    }

    /**
     * 解析并验签。
     *
     * @return 解析成功返回 LoginUser
     * @throws JwtException 签名不符、已过期或格式非法
     */
    public LoginUser parse(String token) throws JwtException {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return new LoginUser(
                Long.valueOf(claims.getSubject()),
                claims.get(CLAIM_USERNAME, String.class),
                claims.get(CLAIM_ROLE, String.class)
        );
    }
}
