package com.smartticket.backend.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class IdempotencyService {

    private final StringRedisTemplate redisTemplate;

    public IdempotencyService(
            StringRedisTemplate redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    public boolean tryAcquire(
            Long userId,
            String requestId
    ) {

        String key =
                "idempotency:ticket:"
                        + userId
                        + ":"
                        + requestId;

        Boolean success =
                redisTemplate.opsForValue()
                        .setIfAbsent(
                                key,
                                "1",
                                Duration.ofMinutes(5)
                        );

        return Boolean.TRUE.equals(success);
    }

    public void release(
            Long userId,
            String requestId
    ) {

        String key =
                "idempotency:ticket:"
                        + userId
                        + ":"
                        + requestId;

        redisTemplate.delete(key);
    }
}