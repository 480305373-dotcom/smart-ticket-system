package com.smartticket.backend.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class RateLimitService {
  public static final DefaultRedisScript<Long> INCREMENT =
      new DefaultRedisScript<>(
          "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('PEXPIRE',KEYS[1],ARGV[1]);"
              + " end; return n",
          Long.class);
  private final StringRedisTemplate redis;

  @Value("${app.redis.enabled:false}")
  private boolean enabled;

  private record Window(long count, long expiry) {}

  private final ConcurrentHashMap<String, Window> local = new ConcurrentHashMap<>();

  public RateLimitService(StringRedisTemplate redis) {
    this.redis = redis;
  }

  public static String digest(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  public boolean allow(String identity, int limit) {
    long now = System.currentTimeMillis(), bucket = now / 60000;
    String key = "smartticket:rate:" + digest(identity) + ":" + bucket;
    if (enabled)
      return Objects.requireNonNull(redis.execute(INCREMENT, List.of(key), "60000")) <= limit;
    if (local.size() > 10000) local.entrySet().removeIf(e -> e.getValue().expiry() < now);
    if (local.size() > 20000 && !local.containsKey(key)) return false;
    return local
            .compute(
                key,
                (k, v) ->
                    v == null || v.expiry() < now
                        ? new Window(1, now + 60000)
                        : new Window(v.count() + 1, v.expiry()))
            .count()
        <= limit;
  }
}
