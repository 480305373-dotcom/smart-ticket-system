package com.smartticket.backend.config;

import java.time.Duration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "app.redis.enabled",
    havingValue = "true")
@Configuration
@EnableCaching
public class CacheConfig {

  @Bean
  public RedisCacheManager cacheManager(
      RedisConnectionFactory connectionFactory,
      @org.springframework.beans.factory.annotation.Value(
              "${app.cache.key-prefix:smartticket:cache:v3:}")
          String keyPrefix) {

    RedisCacheConfiguration config =
        RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .computePrefixWith(name -> keyPrefix + name + "::")
            .serializeValuesWith(
                org.springframework.data.redis.serializer.RedisSerializationContext
                    .SerializationPair.fromSerializer(
                    new org.springframework.data.redis.serializer.JacksonJsonRedisSerializer<>(
                        com.smartticket.backend.dto.TicketStatistics.class)))
            .disableCachingNullValues();

    // Spring Data Redis 4.1 defaults to asynchronous writes. This application needs
    // committed cache changes and failures to be visible before returning success.
    var writer =
        org.springframework.data.redis.cache.RedisCacheWriter.create(
            connectionFactory,
            options ->
                options
                    .immediateWrites()
                    .batchStrategy(org.springframework.data.redis.cache.BatchStrategies.scan(1000)));
    return RedisCacheManager.builder(writer)
        .withCacheConfiguration("ticketStatistics", config)
        .transactionAware()
        .build();
  }
}
