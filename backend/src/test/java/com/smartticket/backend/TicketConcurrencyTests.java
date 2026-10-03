package com.smartticket.backend;

import static org.junit.jupiter.api.Assertions.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartticket.backend.entity.Ticket;
import com.smartticket.backend.entity.User;
import com.smartticket.backend.enums.TicketStatus;
import com.smartticket.backend.mapper.UserMapper;
import com.smartticket.backend.service.TicketActivityService;
import com.smartticket.backend.service.TicketService;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

/**
 * Separate committed transactions exercise actual PostgreSQL unique constraints and row updates.
 */
@SpringBootTest
@ActiveProfiles("dev")
class TicketConcurrencyTests {
  @Autowired TicketService tickets;
  @Autowired TicketActivityService activities;
  @Autowired UserMapper users;
  @Autowired JdbcTemplate jdbc;

  private Long user(String name) {
    return users.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, name)).getId();
  }

  private Ticket payload(Long owner, String title) {
    var t = new Ticket();
    t.setTitle(title);
    t.setPriority(3);
    t.setUserId(owner);
    return t;
  }

  @Test
  void simultaneousStatusChangesProduceOneHistory() throws Exception {
    Long owner = user("dev_zhangsan"), admin = user("dev_admin1");
    var ticket = tickets.createTicket(payload(owner, "并发状态测试_" + UUID.randomUUID()));
    try (var pool = Executors.newFixedThreadPool(2)) {
      var gate = new CountDownLatch(1);
      Callable<Integer> action =
          () -> {
            gate.await();
            try {
              tickets.updateStatus(ticket.getId(), TicketStatus.IN_PROGRESS, admin);
              return 200;
            } catch (ResponseStatusException e) {
              return e.getStatusCode().value();
            }
          };
      var a = pool.submit(action);
      var b = pool.submit(action);
      gate.countDown();
      int ra = a.get(15, TimeUnit.SECONDS), rb = b.get(15, TimeUnit.SECONDS);
      assertTrue(ra == 200 && (rb == 400 || rb == 409) || rb == 200 && (ra == 400 || ra == 409));
      assertEquals(2, activities.history(ticket.getId()).size());
      assertEquals(TicketStatus.IN_PROGRESS, tickets.getTicketById(ticket.getId()).getStatus());
    } finally {
      tickets.deleteTicketById(ticket.getId(), admin, true);
    }
  }

  @Test
  void simultaneousDuplicateSubmissionsCreateOneTicket() throws Exception {
    Long owner = user("dev_zhangsan");
    String key = UUID.randomUUID().toString();
    String title = "并发创建测试_" + key;
    try (var pool = Executors.newFixedThreadPool(2)) {
      var gate = new CountDownLatch(1);
      Callable<com.smartticket.backend.dto.TicketCreation> action =
          () -> {
            gate.await();
            return tickets.createWithRequest(payload(owner, title), key);
          };
      var a = pool.submit(action);
      var b = pool.submit(action);
      gate.countDown();
      var ra = a.get(15, TimeUnit.SECONDS);
      var rb = b.get(15, TimeUnit.SECONDS);
      assertEquals(ra.ticket().getId(), rb.ticket().getId());
      assertNotEquals(ra.created(), rb.created());
      assertEquals(1, activities.history(ra.ticket().getId()).size());
      assertEquals(
          1L, jdbc.queryForObject("SELECT count(*) FROM ticket WHERE title=?", Long.class, title));
    } finally {
      jdbc.update("DELETE FROM ticket_request WHERE user_id=? AND request_id=?", owner, key);
      jdbc.update("DELETE FROM ticket WHERE user_id=? AND title=?", owner, title);
    }
  }
}
