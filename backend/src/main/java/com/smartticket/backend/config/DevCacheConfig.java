package com.smartticket.backend.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.*;

@Configuration
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "app.redis.enabled",
    havingValue = "false",
    matchIfMissing = true)
public class DevCacheConfig {
  @Bean
  public CacheManager cacheManager() {
    return new ConcurrentMapCacheManager("ticketStatistics");
  }
}
