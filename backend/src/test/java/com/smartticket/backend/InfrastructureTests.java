package com.smartticket.backend;

import static org.junit.jupiter.api.Assertions.*;

import com.smartticket.backend.dto.TicketCreatedEvent;
import com.smartticket.backend.entity.Ticket;
import com.smartticket.backend.listener.TicketAuditListener;
import com.smartticket.backend.service.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** Opt in with -Dinfra.tests=true after starting the project Compose services. */
@SpringBootTest
@ActiveProfiles({"dev", "infra"})
@EnabledIfSystemProperty(named = "infra.tests", matches = "true")
class InfrastructureTests {
  @Autowired JdbcTemplate jdbc;
  @Autowired StringRedisTemplate redis;
  @Autowired TicketService tickets;
  @Autowired TicketAuditListener listener;
  @Autowired RateLimitService limiter;
  @Autowired TokenBlacklistService blacklist;
  @Autowired DeadLetterRecoveryService recovery;
  @Autowired org.springframework.amqp.rabbit.core.RabbitTemplate rabbit;

  private Long owner() {
    return jdbc.queryForObject("SELECT id FROM app_user WHERE username='dev_zhangsan'", Long.class);
  }

  @Test
  void redisWindowIsAtomicAndHasTtl() throws Exception {
    String identity = "infra-test:" + UUID.randomUUID();
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(8)) {
      var calls = new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
      for (int i = 0; i < 30; i++) calls.add(pool.submit(() -> limiter.allow(identity, 5)));
      long allowed = 0;
      for (var call : calls) if (call.get()) allowed++;
      assertEquals(5, allowed);
      var key =
          "smartticket:rate:"
              + RateLimitService.digest(identity)
              + ":"
              + System.currentTimeMillis() / 60000;
      assertTrue(redis.getExpire(key) > 0);
      redis.delete(key);
    }
  }

  @Test
  void redisLossDoesNotRestoreRevokedToken() {
    String token = "infra-revocation-" + UUID.randomUUID();
    blacklist.blacklist(token, 60000);
    redis.delete("smartticket:jwt:blacklist:" + RateLimitService.digest(token));
    assertTrue(blacklist.isBlacklisted(token));
    jdbc.update("DELETE FROM token_blacklist WHERE token_hash=?", RateLimitService.digest(token));
  }

  @Test
  void outboxReachesBrokerAndAuditIsExactlyOnce() throws Exception {
    var t = new Ticket();
    t.setTitle("infra-outbox-" + UUID.randomUUID());
    t.setPriority(3);
    t.setUserId(owner());
    var created = tickets.createTicket(t);
    try {
      String id =
          jdbc.queryForObject(
              "SELECT event_id FROM event_outbox WHERE (payload::jsonb->>'ticketId')::bigint=?",
              String.class,
              created.getId());
      long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(25);
      while (System.nanoTime() < deadline
          && jdbc.queryForObject(
                  "SELECT count(*) FROM processed_message WHERE event_id=?", Long.class, id)
              == 0) Thread.sleep(200);
      assertEquals(
          "SENT",
          jdbc.queryForObject(
              "SELECT status FROM event_outbox WHERE event_id=?", String.class, id));
      assertEquals(
          1L,
          jdbc.queryForObject(
              "SELECT count(*) FROM processed_message WHERE event_id=?", Long.class, id));
      var replay = new TicketCreatedEvent(t.getUserId(), created.getId(), "TEST_REPLAY");
      replay.setEventId(id);
      listener.handleTicketCreated(replay);
      listener.handleTicketCreated(replay);
      assertEquals(
          1L,
          jdbc.queryForObject(
              "SELECT count(*) FROM operation_log WHERE target_id=? AND"
                  + " action='CREATE_TICKET_ASYNC'",
              Long.class,
              created.getId()));
    } finally {
      jdbc.update(
          "DELETE FROM processed_message WHERE event_id IN(SELECT event_id FROM event_outbox WHERE"
              + " (payload::jsonb->>'ticketId')::bigint=?)",
          created.getId());
      jdbc.update(
          "DELETE FROM event_outbox WHERE (payload::jsonb->>'ticketId')::bigint=?",
          created.getId());
      tickets.deleteTicketById(created.getId(), t.getUserId(), false);
    }
  }

  @Test
  void statisticsCacheIsJsonAndPermissionScoped() {
    tickets.getStatistics(owner(), false);
    var key = "smartticket:cache:v3:ticketStatistics::USER_" + owner();
    String json = redis.opsForValue().get(key);
    assertNotNull(json);
    assertTrue(json.contains("total"));
    assertTrue(redis.getExpire(key) > 0);
    assertEquals(0, tickets.getStatistics(owner(), false).getUserCount());
  }

  @Test
  void failedConsumerRollsBackInboxAndCanRetry() {
    String id = "inbox-test-" + UUID.randomUUID();
    var event = new TicketCreatedEvent(Long.MAX_VALUE, 1L, "TEST");
    event.setEventId(id);
    try {
      assertThrows(
          org.springframework.dao.DataIntegrityViolationException.class,
          () -> listener.handleTicketCreated(event));
      assertEquals(
          0L,
          jdbc.queryForObject(
              "SELECT count(*) FROM processed_message WHERE event_id=?", Long.class, id));
      event.setUserId(owner());
      listener.handleTicketCreated(event);
      assertEquals(
          1L,
          jdbc.queryForObject(
              "SELECT count(*) FROM processed_message WHERE event_id=?", Long.class, id));
    } finally {
      jdbc.update("DELETE FROM processed_message WHERE event_id=?", id);
      jdbc.update("DELETE FROM operation_log WHERE detail LIKE ?", "%eventId：" + id);
    }
  }

  @Test
  void deadLetterIsPersistedBeforeAcknowledgement() throws Exception {
    org.junit.jupiter.api.Assumptions.assumeTrue(
        cleanTestDeadLetters(),
        "Shared DLQ contains business messages; do not consume them in tests");
    var t = new Ticket();
    t.setTitle("infra-dlq-" + UUID.randomUUID());
    t.setPriority(3);
    t.setUserId(owner());
    var created = tickets.createTicket(t);
    var event = new TicketCreatedEvent(t.getUserId(), created.getId(), "DLQ_TEST");
    try {
      rabbit.convertAndSend(
          com.smartticket.backend.config.RabbitMqConfig.TICKET_DLX,
          com.smartticket.backend.config.RabbitMqConfig.TICKET_DEAD_ROUTING_KEY,
          event);
      com.smartticket.backend.dto.TicketCreatedEvent replay = null;
      for (int i = 0; i < 20 && replay == null; i++) {
        replay = recovery.replayOne();
        if (replay == null) Thread.sleep(100);
      }
      assertNotNull(replay);
      assertEquals(event.getEventId(), replay.getEventId());
      assertEquals(
          1L,
          jdbc.queryForObject(
              "SELECT count(*) FROM event_outbox WHERE event_id=?",
              Long.class,
              event.getEventId()));
      long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(20);
      while (System.nanoTime() < deadline
          && jdbc.queryForObject(
                  "SELECT count(*) FROM processed_message WHERE event_id=?",
                  Long.class,
                  event.getEventId())
              == 0) Thread.sleep(200);
      assertEquals(
          1L,
          jdbc.queryForObject(
              "SELECT count(*) FROM processed_message WHERE event_id=?",
              Long.class,
              event.getEventId()));
    } finally {
      jdbc.update(
          "DELETE FROM processed_message WHERE event_id IN(SELECT event_id FROM event_outbox WHERE"
              + " (payload::jsonb->>'ticketId')::bigint=?)",
          created.getId());
      jdbc.update(
          "DELETE FROM event_outbox WHERE (payload::jsonb->>'ticketId')::bigint=?",
          created.getId());
      tickets.deleteTicketById(created.getId(), owner(), false);
      cleanTestDeadLetters();
    }
  }

  private boolean cleanTestDeadLetters() {
    return Boolean.TRUE.equals(
        rabbit.execute(
            channel -> {
              var json = tools.jackson.databind.json.JsonMapper.builder().build();
              for (int i = 0; i < 30; i++) {
                var item =
                    channel.basicGet(
                        com.smartticket.backend.config.RabbitMqConfig.TICKET_AUDIT_DLQ, false);
                if (item == null) return true;
                var event = json.readValue(item.getBody(), TicketCreatedEvent.class);
                if (!"DLQ_TEST".equals(event.getSource())) {
                  channel.basicNack(item.getEnvelope().getDeliveryTag(), false, true);
                  return false;
                }
                channel.basicAck(item.getEnvelope().getDeliveryTag(), false);
                jdbc.update("DELETE FROM processed_message WHERE event_id=?", event.getEventId());
                jdbc.update("DELETE FROM event_outbox WHERE event_id=?", event.getEventId());
              }
              return false;
            }));
  }
}
