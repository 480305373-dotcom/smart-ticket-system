package com.smartticket.backend.config;

import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Configuration;

/** Cache is an optimization; revocation and request limits keep their own safety policies. */
@Configuration
public class CacheFailureConfig implements CachingConfigurer {
  @Override
  public CacheErrorHandler errorHandler() {
    return new CacheErrorHandler() {
      private void warn(RuntimeException e) {
        org.slf4j.LoggerFactory.getLogger(CacheFailureConfig.class)
            .warn("Cache unavailable; reading PostgreSQL: {}", e.getClass().getSimpleName());
      }

      public void handleCacheGetError(RuntimeException e, Cache c, Object k) {
        warn(e);
      }

      public void handleCachePutError(RuntimeException e, Cache c, Object k, Object v) {
        warn(e);
      }

      public void handleCacheEvictError(RuntimeException e, Cache c, Object k) {
        throw e;
      }

      public void handleCacheClearError(RuntimeException e, Cache c) {
        throw e;
      }
    };
  }
}
