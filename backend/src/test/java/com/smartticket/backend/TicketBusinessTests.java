package com.smartticket.backend;

import static org.junit.jupiter.api.Assertions.*;

import com.smartticket.backend.dto.ArticleRequest;
import com.smartticket.backend.entity.Ticket;
import com.smartticket.backend.entity.User;
import com.smartticket.backend.enums.TicketStatus;
import com.smartticket.backend.mapper.UserMapper;
import com.smartticket.backend.service.*;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Exercises the real PostgreSQL/MyBatis services; each test rolls back its rows. */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class TicketBusinessTests {
  @Autowired TicketService tickets;
  @Autowired TicketActivityService activities;
  @Autowired ArticleService articles;
  @Autowired UserMapper users;
  @Autowired PasswordEncoder encoder;
  @Autowired TokenBlacklistService blacklist;
  @Autowired JwtService jwt;
  @Autowired UserService userService;
  private User owner, other, admin;

  private User user(String role) {
    var u = new User();
    u.setUsername("test_" + UUID.randomUUID());
    u.setRole(role);
    u.setPasswordHash(encoder.encode("Test@12345"));
    users.insert(u);
    return u;
  }

  @BeforeEach
  void prepare() {
    owner = user("USER");
    other = user("USER");
    admin = user("ADMIN");
  }

  private Ticket create() {
    var t = new Ticket();
    t.setTitle("VPN认证失败");
    t.setDescription("VPN无法连接公司网络");
    t.setPriority(4);
    t.setUserId(owner.getId());
    return tickets.createTicket(t);
  }

  @Test
  void createAlwaysStartsOpenWithHistoryAndServerTime() {
    var t = new Ticket();
    t.setTitle("网络故障");
    t.setPriority(3);
    t.setUserId(owner.getId());
    t.setStatus(TicketStatus.CLOSED);
    t.setCreatedAt(java.time.LocalDateTime.of(2000, 1, 1, 0, 0));
    t = tickets.createTicket(t);
    assertEquals(TicketStatus.OPEN, t.getStatus());
    assertTrue(t.getCreatedAt().isAfter(java.time.LocalDateTime.now().minusMinutes(1)));
    var h = activities.history(t.getId());
    assertEquals(1, h.size());
    assertEquals("CREATE", h.getFirst().getAction());
  }

  @Test
  void crossUserAccessIsForbiddenAndAdminCanRead() {
    var t = create();
    var ex =
        assertThrows(
            ResponseStatusException.class,
            () -> tickets.getTicketForUser(t.getId(), other.getId(), false));
    assertEquals(403, ex.getStatusCode().value());
    assertEquals(
        owner.getUsername(),
        tickets.getTicketForUser(t.getId(), admin.getId(), true).getUsername());
  }

  @Test
  void lifecycleIsRecordedAndInvalidTransitionRejected() {
    var t = create();
    assertThrows(
        ResponseStatusException.class,
        () -> tickets.updateStatus(t.getId(), TicketStatus.CLOSED, admin.getId()));
    tickets.updateStatus(t.getId(), TicketStatus.IN_PROGRESS, admin.getId());
    tickets.updateStatus(t.getId(), TicketStatus.CLOSED, admin.getId());
    var history = activities.history(t.getId());
    assertEquals(3, history.size());
    assertEquals("OPEN", history.get(1).getOldStatus());
    assertEquals("IN_PROGRESS", history.get(2).getOldStatus());
    assertEquals("CLOSED", history.get(2).getNewStatus());
  }

  @Test
  void genericUpdateCannotEscalateStatusOrChangeOwner() {
    var t = create();
    var patch = new Ticket();
    patch.setTitle("更新描述");
    patch.setPriority(2);
    patch.setStatus(TicketStatus.CLOSED);
    assertThrows(
        ResponseStatusException.class,
        () -> tickets.updateTicket(t.getId(), patch, owner.getId(), false));
    patch.setStatus(null);
    patch.setUserId(other.getId());
    var updated = tickets.updateTicket(t.getId(), patch, owner.getId(), false);
    assertEquals(owner.getId(), updated.getUserId());
    assertEquals(TicketStatus.OPEN, updated.getStatus());
    assertEquals(t.getCreatedAt(), updated.getCreatedAt());
  }

  @Test
  void pageAndStatisticsOnlyIncludeOwnerData() {
    create();
    var page = tickets.getTicketPage(1, 10, null, "VPN", 4, null, owner.getId(), false);
    assertEquals(1, page.getTotal());
    assertEquals(0, tickets.getStatistics(other.getId(), false).getTotal());
    assertEquals(1, tickets.getStatistics(owner.getId(), false).getHighPriority());
    assertThrows(
        ResponseStatusException.class,
        () -> tickets.getTicketPage(0, 1000, null, null, null, null, owner.getId(), false));
  }

  @Test
  void draftsAreHiddenFromReadsAndRecommendations() {
    var t = create();
    var draft =
        articles.save(
            null,
            new ArticleRequest("VPN专用测试", "测试摘要", "测试正文", "测试分类", "VPN,认证", "DRAFT"),
            admin.getId());
    assertThrows(ResponseStatusException.class, () -> articles.get(draft.getId(), false));
    assertTrue(articles.recommend(t).stream().noneMatch(a -> a.getId().equals(draft.getId())));
    assertTrue(articles.score(t, draft) > 0);
    assertFalse(articles.recommend(t).isEmpty());
    assertTrue(articles.recommend(t).stream().allMatch(a -> "PUBLISHED".equals(a.getStatus())));
  }

  @Test
  void deletionCascadesCommentsAndHistory() {
    var t = create();
    activities.comment(t.getId(), owner.getId(), "补充故障信息");
    assertEquals(1, activities.comments(t.getId()).size());
    tickets.deleteTicketById(t.getId(), owner.getId(), false);
    assertTrue(activities.comments(t.getId()).isEmpty());
    assertTrue(activities.history(t.getId()).isEmpty());
  }

  @Test
  void logoutBlacklistAndJwtClaimsAreReal() {
    String token = jwt.generateToken(owner);
    assertEquals(owner.getUsername(), jwt.parseToken(token).getSubject());
    assertEquals("USER", jwt.parseToken(token).get("role"));
    assertFalse(blacklist.isBlacklisted(token));
    blacklist.blacklist(token, 60000);
    assertTrue(blacklist.isBlacklisted(token));
    String newToken = jwt.generateToken(owner);
    assertNotEquals(token, newToken);
    assertFalse(blacklist.isBlacklisted(newToken));
  }

  @Test
  void assignmentKeepsOwnershipAndRecordsOnlyRealChanges() {
    var t = create();
    var assigned = tickets.assign(t.getId(), admin.getId(), admin.getId());
    assertEquals(owner.getId(), assigned.getUserId());
    assertEquals(admin.getUsername(), assigned.getAssigneeUsername());
    tickets.assign(t.getId(), admin.getId(), admin.getId());
    assertEquals(2, activities.history(t.getId()).size());
    var h = activities.history(t.getId()).getLast();
    assertEquals("ASSIGN", h.getAction());
    assertNull(h.getOldAssigneeId());
    assertEquals(admin.getUsername(), h.getNewAssigneeUsername());
    assertEquals(
        1,
        tickets
            .getTicketPage(1, 10, null, null, null, null, admin.getId(), true, null, true)
            .getTotal());
    assertEquals(
        0,
        tickets
            .getTicketPage(1, 10, null, null, null, null, other.getId(), true, null, true)
            .getTotal());
    assertThrows(
        ResponseStatusException.class,
        () -> tickets.getTicketForUser(t.getId(), other.getId(), false));
    assertThrows(
        ResponseStatusException.class,
        () ->
            tickets.getTicketPage(1, 10, null, null, null, null, owner.getId(), false, null, true));
    tickets.assign(t.getId(), null, admin.getId());
    assertNull(tickets.getTicketById(t.getId()).getAssigneeId());
    assertEquals(3, activities.history(t.getId()).size());
  }

  @Test
  void assignmentRejectsOrdinaryUsersAndCompletedTickets() {
    var t = create();
    assertEquals(
        403,
        assertThrows(
                ResponseStatusException.class,
                () -> tickets.assign(t.getId(), admin.getId(), owner.getId()))
            .getStatusCode()
            .value());
    assertEquals(
        400,
        assertThrows(
                ResponseStatusException.class,
                () -> tickets.assign(t.getId(), other.getId(), admin.getId()))
            .getStatusCode()
            .value());
    tickets.updateStatus(t.getId(), TicketStatus.IN_PROGRESS, admin.getId());
    tickets.updateStatus(t.getId(), TicketStatus.CLOSED, admin.getId());
    assertThrows(
        ResponseStatusException.class,
        () -> tickets.assign(t.getId(), admin.getId(), admin.getId()));
  }

  @Test
  void duplicateRequestReturnsOriginalTicketAndRejectsDifferentPayload() {
    String requestId = UUID.randomUUID().toString();
    var t = new Ticket();
    t.setTitle("重试VPN工单");
    t.setPriority(3);
    t.setUserId(owner.getId());
    t.setAssigneeId(admin.getId());
    var first = tickets.createWithRequest(t, requestId);
    assertTrue(first.created());
    assertNull(first.ticket().getAssigneeId());
    var replay = tickets.createWithRequest(t, requestId);
    assertFalse(replay.created());
    assertEquals(first.ticket().getId(), replay.ticket().getId());
    assertEquals(1, activities.history(first.ticket().getId()).size());
    t.setTitle("不同内容");
    assertEquals(
        409,
        assertThrows(ResponseStatusException.class, () -> tickets.createWithRequest(t, requestId))
            .getStatusCode()
            .value());
  }

  @Test
  void registrationUsesBcryptAndDuplicateUserIsConflict() {
    var request = new com.smartticket.backend.dto.RegisterRequest();
    request.setUsername("register_" + UUID.randomUUID());
    request.setPassword("Test@12345");
    var registered = userService.register(request);
    assertEquals("USER", registered.getRole());
    assertNotEquals(request.getPassword(), registered.getPasswordHash());
    assertTrue(encoder.matches(request.getPassword(), registered.getPasswordHash()));
    var ex = assertThrows(ResponseStatusException.class, () -> userService.register(request));
    assertEquals(409, ex.getStatusCode().value());
    var login = new com.smartticket.backend.dto.LoginRequest();
    login.setUsername(request.getUsername());
    login.setPassword(request.getPassword());
    assertEquals(registered.getId(), userService.login(login).getId());
  }
}
