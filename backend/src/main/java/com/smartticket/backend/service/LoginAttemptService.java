package com.smartticket.backend.service;

import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class LoginAttemptService {
  private final StringRedisTemplate redis;

  @Value("${app.redis.enabled:true}")
  private boolean enabled;

  private record Failure(long count, long expires) {}

  private final ConcurrentHashMap<String, Failure> failures = new ConcurrentHashMap<>();

  public LoginAttemptService(StringRedisTemplate redis) {
    this.redis = redis;
  }

  public boolean isBlocked(String username) {
    if (enabled) {
      String n =
          redis.opsForValue().get("smartticket:login:fail:" + RateLimitService.digest(username));
      return n != null && Long.parseLong(n) >= 5;
    }
    Failure f = failures.get(username);
    return f != null && f.expires() > System.currentTimeMillis() && f.count() >= 5;
  }

  public long recordFailure(String username) {
    if (enabled) {
      Long n =
          redis.execute(
              RateLimitService.INCREMENT,
              java.util.List.of("smartticket:login:fail:" + RateLimitService.digest(username)),
              "600000");
      return n == null ? 0 : n;
    }
    long now = System.currentTimeMillis();
    failures.entrySet().removeIf(e -> e.getValue().expires() < now);
    return failures
        .compute(
            username,
            (k, f) ->
                f == null || f.expires() < now
                    ? new Failure(1, now + 600000)
                    : new Failure(f.count() + 1, f.expires()))
        .count();
  }

  public void clearFailures(String username) {
    if (enabled) redis.delete("smartticket:login:fail:" + RateLimitService.digest(username));
    else failures.remove(username);
  }
}
