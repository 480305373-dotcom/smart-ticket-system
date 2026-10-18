package com.smartticket.backend;

import static org.junit.jupiter.api.Assertions.*;

import com.smartticket.backend.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class AccountSecurityTests {
  @Autowired AccountSecurityService accounts;
  @Autowired UserService users;
  @Autowired JdbcTemplate jdbc;

  private Long create() {
    var r = new com.smartticket.backend.dto.RegisterRequest();
    r.setUsername("security_" + java.util.UUID.randomUUID());
    r.setPassword("Old@123456");
    return users.register(r).getId();
  }

  @Test
  void passwordChangeInvalidatesAllOldSessions() {
    Long id = create();
    assertTrue(accounts.valid(id, "USER", 0));
    accounts.password(id, "Old@123456", "New@123456");
    assertFalse(accounts.valid(id, "USER", 0));
    assertTrue(accounts.valid(id, "USER", 1));
  }

  @Test
  void disableAndRoleChangeInvalidateOldClaims() {
    Long id = create(),
        admin =
            jdbc.queryForObject("SELECT id FROM app_user WHERE username='dev_admin1'", Long.class);
    accounts.update(id, "ADMIN", true, admin);
    assertFalse(accounts.valid(id, "USER", 0));
    assertTrue(accounts.valid(id, "ADMIN", 1));
    accounts.update(id, "ADMIN", false, admin);
    assertFalse(accounts.valid(id, "ADMIN", 2));
  }

  @Test
  void administratorCannotDisableSelf() {
    Long admin =
        jdbc.queryForObject("SELECT id FROM app_user WHERE username='dev_admin1'", Long.class);
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> accounts.update(admin, "USER", false, admin));
  }

  @Test
  void disablingHandlerReleasesActiveAssignmentsWithHistory() {
    Long handler = create(),
        owner = create(),
        admin =
            jdbc.queryForObject("SELECT id FROM app_user WHERE username='dev_admin1'", Long.class);
    jdbc.update("UPDATE app_user SET role='ADMIN' WHERE id=?", handler);
    Long ticket =
        jdbc.queryForObject(
            "INSERT INTO ticket(title,priority,user_id,assignee_id) VALUES"
                + " ('handler-disable-test',3,?,?) RETURNING id",
            Long.class,
            owner,
            handler);
    accounts.update(handler, "ADMIN", false, admin);
    assertNull(
        jdbc.queryForObject("SELECT assignee_id FROM ticket WHERE id=?", Long.class, ticket));
    assertEquals(
        handler,
        jdbc.queryForObject(
            "SELECT old_assignee_id FROM ticket_history WHERE ticket_id=? AND action='ASSIGN'",
            Long.class,
            ticket));
  }
}
