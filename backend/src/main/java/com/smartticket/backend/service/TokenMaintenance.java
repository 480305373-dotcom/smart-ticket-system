package com.smartticket.backend.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TokenMaintenance {
  private final JdbcTemplate jdbc;

  public TokenMaintenance(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Scheduled(fixedDelay = 3600000, initialDelay = 60000)
  public void expire() {
    try {
      jdbc.update("DELETE FROM token_blacklist WHERE expires_at<CURRENT_TIMESTAMP");
    } catch (org.springframework.dao.DataAccessException e) {
      org.slf4j.LoggerFactory.getLogger(getClass()).warn("Expired token cleanup deferred");
    }
  }
}
