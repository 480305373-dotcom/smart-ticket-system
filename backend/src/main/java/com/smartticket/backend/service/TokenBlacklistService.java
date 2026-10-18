package com.smartticket.backend.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class TokenBlacklistService {
  private final StringRedisTemplate redis;
  private final JdbcTemplate jdbc;

  @Value("${app.redis.enabled:true}")
  private boolean redisEnabled;

  public TokenBlacklistService(StringRedisTemplate redis, JdbcTemplate jdbc) {
    this.redis = redis;
    this.jdbc = jdbc;
  }

  private String hash(String token) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  public void blacklist(String token, long ttlMillis) {
    if (redisEnabled) {
      // Persist first; Redis loss/restart must never restore a revoked token.
      jdbc.update(
          "INSERT INTO token_blacklist(token_hash,expires_at) VALUES (?,?) ON CONFLICT(token_hash)"
              + " DO UPDATE SET expires_at=EXCLUDED.expires_at",
          hash(token),
          java.sql.Timestamp.from(java.time.Instant.now().plusMillis(ttlMillis)));
      try {
        redis
            .opsForValue()
            .set("smartticket:jwt:blacklist:" + hash(token), "1", Duration.ofMillis(ttlMillis));
      } catch (org.springframework.dao.DataAccessException e) {
        org.slf4j.LoggerFactory.getLogger(getClass())
            .warn("Revocation persisted; Redis cache unavailable");
      }
      return;
    }
    jdbc.update("DELETE FROM token_blacklist WHERE expires_at < CURRENT_TIMESTAMP");
    jdbc.update(
        "INSERT INTO token_blacklist(token_hash, expires_at) VALUES (?,?) ON CONFLICT(token_hash)"
            + " DO UPDATE SET expires_at=EXCLUDED.expires_at",
        hash(token),
        java.sql.Timestamp.from(java.time.Instant.now().plusMillis(ttlMillis)));
  }

  public boolean isBlacklisted(String token) {
    if (redisEnabled)
      try {
        if (Boolean.TRUE.equals(redis.hasKey("smartticket:jwt:blacklist:" + hash(token))))
          return true;
      } catch (org.springframework.dao.DataAccessException e) {
        /* PostgreSQL remains authoritative. */
      }
    return jdbc.queryForObject(
            "SELECT count(*) FROM token_blacklist WHERE token_hash=? AND"
                + " expires_at>CURRENT_TIMESTAMP",
            Long.class,
            hash(token))
        > 0;
  }
}
