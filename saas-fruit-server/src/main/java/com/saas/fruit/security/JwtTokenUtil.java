package com.saas.fruit.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT Token工具类
 * 负责生成、解析和验证JWT Token
 */
@Component
public class JwtTokenUtil {

    @Value("${app.jwt-secret}")
    private String jwtSecret;

    @Value("${app.jwt-expiration}")
    private long jwtExpiration;

    /** 获取签名密钥 */
    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    /** 生成JWT Token */
    public String generateToken(Long userId, String openid, String tenantId, String role) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + jwtExpiration);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("openid", openid)
                .claim("tenantId", tenantId)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(getKey())
                .compact();
    }

    /** 解析JWT Token */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new RuntimeException("Token已过期");
        } catch (JwtException e) {
            throw new RuntimeException("Token无效");
        }
    }

    /** 从Token中提取登录用户信息 */
    public LoginUser parseLoginUser(String token) {
        Claims claims = parseToken(token);
        LoginUser user = new LoginUser();
        user.setUserId(Long.parseLong(claims.getSubject()));
        user.setOpenid(claims.get("openid", String.class));
        user.setTenantId(claims.get("tenantId", String.class));
        user.setRole(claims.get("role", String.class));
        return user;
    }
}
