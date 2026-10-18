package com.smartticket.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("prod")
public class BootstrapAdmin implements CommandLineRunner {
  private final JdbcTemplate jdbc;
  private final PasswordEncoder encoder;

  @Value("${ADMIN_USERNAME:}")
  private String username;

  @Value("${ADMIN_PASSWORD:}")
  private String password;

  public BootstrapAdmin(JdbcTemplate jdbc, PasswordEncoder encoder) {
    this.jdbc = jdbc;
    this.encoder = encoder;
  }

  @Override
  @Transactional
  public void run(String... args) {
    jdbc.execute("SELECT pg_advisory_xact_lock(741852965)");
    if (jdbc.queryForObject(
            "SELECT count(*) FROM app_user WHERE role='ADMIN' AND enabled=TRUE", Long.class)
        > 0) return;
    if (!username.matches("[A-Za-z0-9_]{3,50}")
        || password.length() < 12
        || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw new IllegalArgumentException(
          "Set ADMIN_USERNAME and a private ADMIN_PASSWORD (12-72 bytes) to bootstrap the first"
              + " production administrator");
    jdbc.update(
        "INSERT INTO app_user(username,password_hash,role) VALUES (?,?,'ADMIN')",
        username,
        encoder.encode(password));
  }
}
