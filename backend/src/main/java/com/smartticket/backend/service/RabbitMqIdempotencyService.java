package com.smartticket.backend.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RabbitMqIdempotencyService {

    private static final String KEY_PREFIX =
            "mq:processed:";

    private static final Duration TTL =
            Duration.ofDays(7);

    private final StringRedisTemplate redisTemplate;

    public RabbitMqIdempotencyService(
            StringRedisTemplate redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 尝试占用消息处理权。
     *
     * true  = 第一次处理
     * false = 已经处理过，不允许重复处理
     */
    public boolean tryAcquire(String eventId) {

        Boolean success =
                redisTemplate.opsForValue()
                        .setIfAbsent(
                                KEY_PREFIX + eventId,
                                "1",
                                TTL
                        );

        return Boolean.TRUE.equals(success);
    }

    /**
     * 业务处理失败时释放，
     * 允许 RabbitMQ 下一次重试继续处理。
     */
    public void release(String eventId) {

        redisTemplate.delete(
                KEY_PREFIX + eventId
        );
    }
}