package com.smartticket.backend.controller;

import com.smartticket.backend.dto.ApiResponse;
import com.smartticket.backend.service.OutboxService;
import java.util.*;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
public class SystemController {
  private final JdbcTemplate jdbc;
  private final StringRedisTemplate redis;
  private final org.springframework.beans.factory.ObjectProvider<AmqpAdmin> rabbit;
  private final OutboxService outbox;

  @Value("${app.redis.enabled:false}")
  private boolean redisEnabled;

  @Value("${app.mq.enabled:false}")
  private boolean mqEnabled;

  @Value("${app.ai.enabled:false}")
  private boolean aiEnabled;

  @Value("${app.registration.enabled:true}")
  private boolean registrationEnabled;

  public SystemController(
      JdbcTemplate jdbc,
      StringRedisTemplate redis,
      org.springframework.beans.factory.ObjectProvider<AmqpAdmin> rabbit,
      OutboxService outbox) {
    this.jdbc = jdbc;
    this.redis = redis;
    this.rabbit = rabbit;
    this.outbox = outbox;
  }

  @GetMapping("/system/capabilities")
  public ApiResponse<?> capabilities() {
    return ApiResponse.success(
        Map.of("redis", redisEnabled, "rabbitmq", mqEnabled, "ai", aiEnabled));
  }

  @GetMapping("/system/public-config")
  public ApiResponse<?> publicConfig() {
    return ApiResponse.success(Map.of("registrationEnabled", registrationEnabled));
  }

  @GetMapping("/admin/system")
  public ApiResponse<?> status() {
    var data = new LinkedHashMap<String, Object>();
    data.put("postgres", jdbc.queryForObject("SELECT 1", Integer.class) == 1 ? "UP" : "DOWN");
    String redisStatus = "DISABLED", mqStatus = "DISABLED";
    long queue = 0, dead = 0;
    if (redisEnabled)
      try {
        redisStatus =
            "PONG"
                    .equals(
                        redis.execute(
                            (org.springframework.data.redis.core.RedisCallback<String>)
                                connection -> connection.ping()))
                ? "UP"
                : "DOWN";
      } catch (Exception e) {
        redisStatus = "DOWN";
      }
    if (mqEnabled)
      try {
        var normal =
            rabbit
                .getObject()
                .getQueueProperties(
                    com.smartticket.backend.config.RabbitMqConfig.TICKET_AUDIT_QUEUE);
        var dlq =
            rabbit
                .getObject()
                .getQueueProperties(com.smartticket.backend.config.RabbitMqConfig.TICKET_AUDIT_DLQ);
        mqStatus = normal == null ? "DOWN" : "UP";
        if (normal != null) queue = ((Number) normal.get("QUEUE_MESSAGE_COUNT")).longValue();
        if (dlq != null) dead = ((Number) dlq.get("QUEUE_MESSAGE_COUNT")).longValue();
      } catch (Exception e) {
        mqStatus = "DOWN";
      }
    data.put("redis", redisStatus);
    data.put("rabbitmq", mqStatus);
    data.put("queueDepth", queue);
    data.put("deadLetters", dead);
    data.put("aiEnabled", aiEnabled);
    data.put(
        "outbox",
        jdbc.queryForList("SELECT status,count(*) AS count FROM event_outbox GROUP BY status"));
    data.put(
        "events",
        jdbc.queryForList(
            "SELECT event_id,status,attempts,last_error,created_at,sent_at FROM event_outbox ORDER"
                + " BY created_at DESC LIMIT 30"));
    return ApiResponse.success(data);
  }

  @PostMapping("/admin/outbox/{id}/retry")
  public ApiResponse<?> retry(@PathVariable String id) {
    outbox.retry(id);
    return ApiResponse.success(null);
  }
}
