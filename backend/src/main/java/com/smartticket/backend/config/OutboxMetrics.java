package com.smartticket.backend.config;

import io.micrometer.core.instrument.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class OutboxMetrics {
  public OutboxMetrics(MeterRegistry registry, JdbcTemplate jdbc) {
    for (String status : java.util.List.of("PENDING", "SENT", "FAILED"))
      Gauge.builder(
              "smartticket.outbox.events",
              jdbc,
              j -> {
                try {
                  return j.queryForObject(
                          "SELECT count(*) FROM event_outbox WHERE status=?", Long.class, status)
                      .doubleValue();
                } catch (Exception e) {
                  return Double.NaN;
                }
              })
          .tag("status", status)
          .description("Durable ticket event count")
          .register(registry);
  }
}
