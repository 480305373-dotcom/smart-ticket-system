package com.smartticket.backend.service;

import com.smartticket.backend.config.RabbitMqConfig;
import com.smartticket.backend.dto.TicketCreatedEvent;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** Durable at-least-once delivery. Consumer deduplication is part of its database transaction. */
@Service
public class OutboxService {
  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(OutboxService.class);
  private final JdbcTemplate jdbc;
  private final RabbitTemplate rabbit;
  private final ObjectMapper json;

  @Value("${app.mq.enabled:false}")
  private boolean enabled;

  @Value("${app.outbox.max-attempts:10}")
  private int maxAttempts;

  public OutboxService(JdbcTemplate jdbc, RabbitTemplate rabbit, ObjectMapper json) {
    this.jdbc = jdbc;
    this.rabbit = rabbit;
    this.json = json;
  }

  public void enqueue(TicketCreatedEvent event) {
    jdbc.update(
        "INSERT INTO event_outbox(event_id,payload) VALUES (?,?) ON CONFLICT DO NOTHING",
        event.getEventId(),
        json.writeValueAsString(event));
  }

  @Scheduled(fixedDelayString = "${app.outbox.interval-ms:1000}", initialDelay = 10000)
  public void dispatch() {
    if (!enabled) return;
    // Claim one at a time: a slow confirm cannot consume another worker's lease.
    for (int i = 0; i < 10; i++) {
      String lease = UUID.randomUUID().toString();
      List<Map<String, Object>> rows;
      try {
        rows =
            jdbc.queryForList(
                """
WITH candidate AS (
  SELECT event_id FROM event_outbox WHERE status='PENDING' AND next_attempt_at<=CURRENT_TIMESTAMP
    AND (locked_until IS NULL OR locked_until<CURRENT_TIMESTAMP)
  ORDER BY created_at FOR UPDATE SKIP LOCKED LIMIT 1)
UPDATE event_outbox e SET locked_until=CURRENT_TIMESTAMP+INTERVAL '30 seconds',lock_token=?
  FROM candidate c WHERE e.event_id=c.event_id RETURNING e.event_id,e.payload,e.attempts
""",
                lease);
      } catch (org.springframework.dao.DataAccessException e) {
        log.warn("Outbox database temporarily unavailable");
        return;
      }
      if (rows.isEmpty()) return;
      var row = rows.getFirst();
      String id = (String) row.get("event_id");
      try {
        var event = json.readValue((String) row.get("payload"), TicketCreatedEvent.class);
        var correlation = new CorrelationData(id);
        rabbit.convertAndSend(
            RabbitMqConfig.TICKET_EXCHANGE,
            RabbitMqConfig.TICKET_CREATED_ROUTING_KEY,
            event,
            correlation);
        var confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
        if (!confirm.isAck() || correlation.getReturned() != null)
          throw new IllegalStateException("Broker did not confirm routing");
        jdbc.update(
            "UPDATE event_outbox SET"
                + " status='SENT',sent_at=CURRENT_TIMESTAMP,locked_until=NULL,lock_token=NULL,last_error=NULL"
                + " WHERE event_id=? AND lock_token=?",
            id,
            lease);
      } catch (Exception e) {
        if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        int attempts = ((Number) row.get("attempts")).intValue() + 1;
        jdbc.update(
            "UPDATE event_outbox SET attempts=?,status=?,next_attempt_at=CURRENT_TIMESTAMP+(? *"
                + " INTERVAL '1 second'),locked_until=NULL,lock_token=NULL,last_error=? WHERE"
                + " event_id=? AND lock_token=?",
            attempts,
            attempts >= maxAttempts ? "FAILED" : "PENDING",
            Math.min(300, 1L << Math.min(attempts, 8)),
            e.getClass().getSimpleName(),
            id,
            lease);
        log.warn("Outbox delivery deferred: eventId={}, attempt={}", id, attempts);
        return;
      }
    }
  }

  public void retry(String id) {
    if (jdbc.update(
            "UPDATE event_outbox SET"
                + " status='PENDING',attempts=0,next_attempt_at=CURRENT_TIMESTAMP,last_error=NULL"
                + " WHERE event_id=? AND status='FAILED'",
            id)
        != 1)
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.CONFLICT, "仅可重试失败事件");
  }

  @org.springframework.transaction.annotation.Transactional
  public void requeue(TicketCreatedEvent event) {
    if (event.getEventId() == null || event.getEventId().isBlank())
      event.setEventId("ticket-created-" + event.getTicketId());
    if (event.getEventId().length() > 100
        || event.getUserId() == null
        || event.getTicketId() == null)
      throw new IllegalArgumentException("Invalid dead-letter event");
    jdbc.update(
        """
INSERT INTO event_outbox(event_id,payload) VALUES (?,?)
ON CONFLICT(event_id) DO UPDATE SET status='PENDING',attempts=0,next_attempt_at=CURRENT_TIMESTAMP,
  locked_until=NULL,lock_token=NULL,last_error=NULL
""",
        event.getEventId(),
        json.writeValueAsString(event));
  }
}
