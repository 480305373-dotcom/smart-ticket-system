package com.smartticket.backend.service;

import com.smartticket.backend.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  @Value("${jwt.secret}")
  private String secret;

  @Value("${jwt.expiration}")
  private long expiration;

  private SecretKey getSigningKey() {

    byte[] keyBytes = Decoders.BASE64.decode(secret);

    return Keys.hmacShaKeyFor(keyBytes);
  }

  // 生成 JWT
  public String generateToken(User user) {

    Date now = new Date();

    Date expireAt = new Date(now.getTime() + expiration);

    return Jwts.builder()
        .id(java.util.UUID.randomUUID().toString())
        .subject(user.getUsername())
        .claim("userId", user.getId())
        .claim("role", user.getRole())
        .claim("version", user.getAuthVersion() == null ? 0 : user.getAuthVersion())
        .issuedAt(now)
        .expiration(expireAt)
        .signWith(getSigningKey())
        .compact();
  }

  // 解析 JWT
  public Claims parseToken(String token) {

    return Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token).getPayload();
  }

  // 获取 Token 剩余有效时间（毫秒）
  public long getRemainingExpirationMillis(String token) {

    Claims claims = parseToken(token);

    long expirationTime = claims.getExpiration().getTime();

    long now = System.currentTimeMillis();

    return Math.max(expirationTime - now, 0);
  }
}
