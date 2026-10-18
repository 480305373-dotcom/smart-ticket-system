package com.smartticket.backend.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Inbox marker and business effect share the listener transaction. */
@Service
public class RabbitMqIdempotencyService {
  private final JdbcTemplate jdbc;

  public RabbitMqIdempotencyService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public boolean tryAcquire(String eventId) {
    return jdbc.update(
            "INSERT INTO processed_message(event_id) VALUES (?) ON CONFLICT DO NOTHING", eventId)
        == 1;
  }
}
