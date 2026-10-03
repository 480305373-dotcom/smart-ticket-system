package com.smartticket.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartticket.backend.dto.TicketStatistics;
import com.smartticket.backend.entity.Ticket;
import com.smartticket.backend.enums.TicketStatus;
import com.smartticket.backend.mapper.TicketMapper;
import java.util.List;
import java.util.Objects;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TicketService {

  private final TicketMapper ticketMapper;
  private final TicketActivityService activities;
  private final UserDirectoryService directory;
  private final com.smartticket.backend.mapper.TicketRequestMapper requests;
  private final com.smartticket.backend.mapper.UserMapper users;

  public TicketService(
      TicketMapper ticketMapper,
      TicketActivityService activities,
      com.smartticket.backend.mapper.UserMapper users,
      UserDirectoryService directory,
      com.smartticket.backend.mapper.TicketRequestMapper requests) {
    this.ticketMapper = ticketMapper;
    this.activities = activities;
    this.users = users;
    this.directory = directory;
    this.requests = requests;
  }

  private Ticket enrich(Ticket ticket) {
    if (ticket != null) enrichAll(java.util.List.of(ticket));
    return ticket;
  }

  private List<Ticket> enrichAll(List<Ticket> list) {
    var ids =
        list.stream()
            .flatMap(t -> java.util.stream.Stream.of(t.getUserId(), t.getAssigneeId()))
            .toList();
    var names = directory.names(ids);
    list.forEach(
        t -> {
          t.setUsername(names.get(t.getUserId()));
          t.setAssigneeUsername(names.get(t.getAssigneeId()));
        });
    return list;
  }

  public static void validatePage(long current, long size) {
    if (current < 1 || size < 1 || size > 100)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "页码必须大于0，每页数量必须在1到100之间");
  }

  public List<Ticket> getAllTickets() {
    return enrichAll(ticketMapper.selectList(null));
  }

  @CacheEvict(value = "ticketStatistics", allEntries = true)
  @org.springframework.transaction.annotation.Transactional
  public Ticket createTicket(Ticket ticket) {
    ticket.setId(null);
    ticket.setStatus(TicketStatus.OPEN);
    ticket.setAssigneeId(null);
    ticket.setCreatedAt(null);
    ticket.setUpdatedAt(null);
    ticketMapper.insert(ticket);
    activities.record(ticket.getId(), ticket.getUserId(), "CREATE", null, "OPEN");
    return enrich(ticketMapper.selectById(ticket.getId()));
  }

  public Ticket getTicketById(Long id) {
    return enrich(ticketMapper.selectById(id));
  }

  @CacheEvict(value = "ticketStatistics", allEntries = true)
  @org.springframework.transaction.annotation.Transactional
  public Ticket updateTicket(Long id, Ticket ticket, Long currentUserId, boolean admin) {

    Ticket existing = getTicketForUser(id, currentUserId, admin);

    ticket.setId(id);
    if (ticket.getStatus() != null && ticket.getStatus() != existing.getStatus())
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "请使用管理员状态流转接口修改状态");
    ticket.setStatus(existing.getStatus());
    ticket.setCreatedAt(existing.getCreatedAt());
    ticket.setUpdatedAt(null);

    // 禁止通过请求修改工单所属用户
    ticket.setUserId(existing.getUserId());

    // Update editable fields only; a simultaneous status change must not be overwritten.
    Ticket changes = new Ticket();
    changes.setTitle(ticket.getTitle());
    changes.setPriority(ticket.getPriority());
    changes.setStatus(null);
    int affected =
        ticketMapper.update(
            changes,
            new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Ticket>()
                .eq(Ticket::getId, id)
                .eq(Ticket::getStatus, existing.getStatus())
                .set(Ticket::getDescription, ticket.getDescription()));
    if (affected != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "工单状态已变化，请刷新重试");

    return ticketMapper.selectById(id);
  }

  @CacheEvict(value = "ticketStatistics", allEntries = true)
  @org.springframework.transaction.annotation.Transactional
  public void deleteTicketById(Long id, Long currentUserId, boolean admin) {

    getTicketForUser(id, currentUserId, admin);

    ticketMapper.deleteById(id);
  }

  public List<Ticket> getTicketsByUserId(Long userId) {

    return enrichAll(
        ticketMapper.selectList(new LambdaQueryWrapper<Ticket>().eq(Ticket::getUserId, userId)));
  }

  public Ticket getTicketForUser(Long id, Long currentUserId, boolean admin) {

    Ticket ticket = ticketMapper.selectById(id);

    if (ticket == null) {

      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "工单不存在");
    }

    if (!admin && !Objects.equals(ticket.getUserId(), currentUserId)) {

      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权操作该工单");
    }

    return enrich(ticket);
  }

  @CacheEvict(value = "ticketStatistics", allEntries = true)
  @org.springframework.transaction.annotation.Transactional
  public Ticket updateStatus(Long id, TicketStatus status, Long operatorId) {

    Ticket ticket = ticketMapper.selectById(id);

    if (ticket == null) {

      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "工单不存在");
    }

    TicketStatus old = ticket.getStatus();
    boolean valid =
        old == TicketStatus.OPEN && status == TicketStatus.IN_PROGRESS
            || old == TicketStatus.IN_PROGRESS
                && (status == TicketStatus.CLOSED || status == TicketStatus.RESOLVED)
            || old == TicketStatus.RESOLVED && status == TicketStatus.CLOSED;
    if (!valid)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非法状态流转：" + old + " → " + status);
    // Compare-and-set prevents concurrent handlers from writing conflicting histories.
    Ticket stateChange = new Ticket();
    stateChange.setStatus(status);

    int affected =
        ticketMapper.update(
            stateChange,
            new LambdaQueryWrapper<Ticket>().eq(Ticket::getId, id).eq(Ticket::getStatus, old));
    if (affected != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "工单状态已变化，请刷新重试");
    activities.record(id, operatorId, "CHANGE_STATUS", old.name(), status.name());
    return enrich(ticketMapper.selectById(id));
  }

  public IPage<Ticket> getTicketPage(
      long current,
      long size,
      TicketStatus status,
      String keyword,
      Integer priority,
      String username,
      Long currentUserId,
      boolean admin) {

    return getTicketPage(
        current, size, status, keyword, priority, username, currentUserId, admin, null, false);
  }

  public IPage<Ticket> getTicketPage(
      long current,
      long size,
      TicketStatus status,
      String keyword,
      Integer priority,
      String username,
      Long currentUserId,
      boolean admin,
      Long assigneeId,
      boolean assignedToMe) {
    validatePage(current, size);
    if ((assigneeId != null || assignedToMe) && !admin)
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有管理员可以查看处理人工作队列");
    if (assigneeId != null && assigneeId < 1)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "处理人ID必须大于0");
    if (assignedToMe && assigneeId != null && !assigneeId.equals(currentUserId))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "处理人筛选与分配给我的条件冲突");
    if (priority != null && (priority < 1 || priority > 5))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "优先级必须在1到5之间");
    Page<Ticket> page = new Page<>(current, size);

    LambdaQueryWrapper<Ticket> wrapper = new LambdaQueryWrapper<>();

    if (!admin) {
      wrapper.eq(Ticket::getUserId, currentUserId);
    }

    if (status != null) {
      wrapper.eq(Ticket::getStatus, status);
    }

    if (StringUtils.hasText(keyword)) {

      wrapper.and(
          w -> w.like(Ticket::getTitle, keyword).or().like(Ticket::getDescription, keyword));
    }

    if (priority != null) wrapper.eq(Ticket::getPriority, priority);
    if (admin && StringUtils.hasText(username)) {
      var ids =
          users
              .selectList(
                  new LambdaQueryWrapper<com.smartticket.backend.entity.User>()
                      .like(com.smartticket.backend.entity.User::getUsername, username))
              .stream()
              .map(com.smartticket.backend.entity.User::getId)
              .toList();
      if (ids.isEmpty()) wrapper.eq(Ticket::getUserId, -1L);
      else wrapper.in(Ticket::getUserId, ids);
    }
    if (assignedToMe) wrapper.eq(Ticket::getAssigneeId, currentUserId);
    else if (assigneeId != null) wrapper.eq(Ticket::getAssigneeId, assigneeId);
    wrapper.orderByDesc(Ticket::getId);

    var result = ticketMapper.selectPage(page, wrapper);
    enrichAll(result.getRecords());
    return result;
  }

  @org.springframework.cache.annotation.Cacheable(
      value = "ticketStatistics",
      key = "#admin ? 'ADMIN' : 'USER_' + #currentUserId")
  public TicketStatistics getStatistics(Long currentUserId, boolean admin) {
    return ticketMapper.statistics(currentUserId, admin);
  }

  @CacheEvict(value = "ticketStatistics", allEntries = true)
  @org.springframework.transaction.annotation.Transactional
  public Ticket assign(Long id, Long assigneeId, Long operatorId) {
    var operator = users.selectById(operatorId);
    if (operator == null || !"ADMIN".equals(operator.getRole()))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有管理员可以指派工单");
    var ticket = getTicketForUser(id, operatorId, true);
    if (ticket.getStatus() == TicketStatus.CLOSED || ticket.getStatus() == TicketStatus.RESOLVED)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "已完成的工单不能重新指派");
    if (assigneeId != null) {
      var handler = users.selectById(assigneeId);
      if (handler == null || !"ADMIN".equals(handler.getRole()))
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "处理人必须是有效的管理员账号");
    }
    if (Objects.equals(ticket.getAssigneeId(), assigneeId)) return ticket;
    var condition =
        new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Ticket>()
            .eq(Ticket::getId, id)
            .in(Ticket::getStatus, TicketStatus.OPEN, TicketStatus.IN_PROGRESS)
            .set(Ticket::getAssigneeId, assigneeId);
    if (ticket.getAssigneeId() == null) condition.isNull(Ticket::getAssigneeId);
    else condition.eq(Ticket::getAssigneeId, ticket.getAssigneeId());
    var changes = new Ticket();
    changes.setStatus(null);
    if (ticketMapper.update(changes, condition) != 1)
      throw new ResponseStatusException(HttpStatus.CONFLICT, "工单状态或处理人已变化，请刷新重试");
    activities.recordAssignment(id, operatorId, ticket.getAssigneeId(), assigneeId);
    return enrich(ticketMapper.selectById(id));
  }

  @CacheEvict(value = "ticketStatistics", allEntries = true)
  @org.springframework.transaction.annotation.Transactional
  public com.smartticket.backend.dto.TicketCreation createWithRequest(
      Ticket ticket, String requestId) {
    if (requestId == null)
      return new com.smartticket.backend.dto.TicketCreation(createTicket(ticket), true);
    if (!requestId.matches("[A-Za-z0-9._:-]{1,100}"))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Request-Id必须为1到100位字母、数字或._:-");
    String fingerprint = fingerprint(ticket);
    if (requests.claim(ticket.getUserId(), requestId, fingerprint) == 0) {
      var previous = requests.find(ticket.getUserId(), requestId);
      if (previous == null || !fingerprint.equals(previous.getFingerprint()))
        throw new ResponseStatusException(HttpStatus.CONFLICT, "同一请求编号不能用于不同工单内容");
      if (previous.getTicketId() == null)
        throw new ResponseStatusException(HttpStatus.CONFLICT, "该请求对应的工单已删除，请使用新的请求编号");
      return new com.smartticket.backend.dto.TicketCreation(
          getTicketForUser(previous.getTicketId(), ticket.getUserId(), false), false);
    }
    var created = createTicket(ticket);
    requests.complete(ticket.getUserId(), requestId, created.getId());
    return new com.smartticket.backend.dto.TicketCreation(created, true);
  }

  private String fingerprint(Ticket ticket) {
    try {
      var digest = java.security.MessageDigest.getInstance("SHA-256");
      // Length-prefix each field so embedded delimiters cannot create ambiguous payloads.
      for (String field :
          java.util.Arrays.asList(
              ticket.getTitle(), ticket.getDescription(), String.valueOf(ticket.getPriority()))) {
        if (field == null) digest.update(java.nio.ByteBuffer.allocate(4).putInt(-1).array());
        else {
          byte[] bytes = field.getBytes(java.nio.charset.StandardCharsets.UTF_8);
          digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());
          digest.update(bytes);
        }
      }
      return java.util.HexFormat.of().formatHex(digest.digest());
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
