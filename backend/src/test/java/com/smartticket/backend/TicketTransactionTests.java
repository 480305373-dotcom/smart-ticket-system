package com.smartticket.backend;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartticket.backend.entity.Ticket;
import com.smartticket.backend.entity.User;
import com.smartticket.backend.mapper.UserMapper;
import com.smartticket.backend.service.TicketActivityService;
import com.smartticket.backend.service.TicketService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest
@ActiveProfiles("dev")
class TicketTransactionTests {
  @Autowired TicketService tickets;
  @Autowired UserMapper users;
  @Autowired JdbcTemplate jdbc;
  @MockitoSpyBean TicketActivityService activities;

  @Test
  void historyFailureRollsBackTicketAndIdempotencyClaim() {
    Long owner =
        users
            .selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, "dev_zhangsan"))
            .getId();
    String key = UUID.randomUUID().toString(), title = "事务回滚测试_" + key;
    var payload = new Ticket();
    payload.setTitle(title);
    payload.setPriority(3);
    payload.setUserId(owner);
    doThrow(
            new org.springframework.dao.DataAccessResourceFailureException(
                "simulated history failure"))
        .when(activities)
        .record(anyLong(), eq(owner), eq("CREATE"), isNull(), eq("OPEN"));
    try {
      assertThrows(
          org.springframework.dao.DataAccessException.class,
          () -> tickets.createWithRequest(payload, key));
      assertEquals(
          0L, jdbc.queryForObject("SELECT count(*) FROM ticket WHERE title=?", Long.class, title));
      assertEquals(
          0L,
          jdbc.queryForObject(
              "SELECT count(*) FROM ticket_request WHERE user_id=? AND request_id=?",
              Long.class,
              owner,
              key));
    } finally {
      reset(activities);
      jdbc.update("DELETE FROM ticket_request WHERE user_id=? AND request_id=?", owner, key);
      jdbc.update("DELETE FROM ticket WHERE user_id=? AND title=?", owner, title);
    }
  }
}
