package com.smartticket.backend.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.smartticket.backend.dto.ApiResponse;
import com.smartticket.backend.dto.TicketStatistics;
import com.smartticket.backend.dto.UpdateTicketStatusRequest;
import com.smartticket.backend.entity.Ticket;
import com.smartticket.backend.enums.TicketStatus;
import com.smartticket.backend.security.AuthUser;
import com.smartticket.backend.service.OperationLogService;
import com.smartticket.backend.service.TicketService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class TicketController {

  private final TicketService ticketService;
  private final OperationLogService operationLogService;
  @org.springframework.beans.factory.annotation.Autowired
  private com.smartticket.backend.service.ArticleService articles;

  public TicketController(TicketService ticketService, OperationLogService operationLogService) {
    this.ticketService = ticketService;
    this.operationLogService = operationLogService;
  }

  // 查询工单
  @GetMapping("/tickets")
  public ApiResponse<List<Ticket>> getAllTickets(Authentication authentication) {
    AuthUser user = (AuthUser) authentication.getPrincipal();

    if ("ADMIN".equals(user.role())) {
      return ApiResponse.success(ticketService.getAllTickets());
    }

    return ApiResponse.success(ticketService.getTicketsByUserId(user.userId()));
  }

  // 创建工单
  @PostMapping("/tickets")
  public ApiResponse<Ticket> createTicket(
      @Valid @RequestBody Ticket ticket,
      Authentication authentication,
      @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
    AuthUser user = (AuthUser) authentication.getPrincipal();

    ticket.setId(null);
    ticket.setUserId(user.userId());

    var result = ticketService.createWithRequest(ticket, requestId);
    Ticket created = result.ticket();

    return ApiResponse.success(created);
  }

  // 根据 ID 查询工单
  @GetMapping("/tickets/{id}")
  public ApiResponse<Ticket> getTicketById(@PathVariable Long id, Authentication authentication) {
    AuthUser user = (AuthUser) authentication.getPrincipal();

    boolean admin = "ADMIN".equals(user.role());

    return ApiResponse.success(ticketService.getTicketForUser(id, user.userId(), admin));
  }

  // 修改工单
  @PutMapping("/tickets/{id}")
  public ApiResponse<Ticket> updateTicket(
      @PathVariable Long id,
      @Valid @RequestBody com.smartticket.backend.dto.TicketUpdateRequest request,
      Authentication authentication) {
    Ticket ticket = new Ticket();
    ticket.setTitle(request.title());
    ticket.setDescription(request.description());
    ticket.setPriority(request.priority());
    ticket.setStatus(request.status());
    AuthUser user = (AuthUser) authentication.getPrincipal();

    boolean admin = "ADMIN".equals(user.role());

    Ticket updated = ticketService.updateTicket(id, ticket, user.userId(), admin);

    operationLogService.record(user.userId(), "UPDATE_TICKET", id, "修改工单");

    return ApiResponse.success(updated);
  }

  // 删除工单
  @DeleteMapping("/tickets/{id}")
  public ApiResponse<Void> deleteTicket(@PathVariable Long id, Authentication authentication) {
    AuthUser user = (AuthUser) authentication.getPrincipal();

    boolean admin = "ADMIN".equals(user.role());

    ticketService.deleteTicketById(id, user.userId(), admin);

    operationLogService.record(user.userId(), "DELETE_TICKET", id, "删除工单");

    return ApiResponse.success(null);
  }

  // 管理员修改工单状态
  @PatchMapping("/tickets/{id}/status")
  public ApiResponse<Ticket> updateStatus(
      @PathVariable Long id,
      @Valid @RequestBody UpdateTicketStatusRequest request,
      Authentication authentication) {
    AuthUser user = (AuthUser) authentication.getPrincipal();

    if (!"ADMIN".equals(user.role())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有管理员可以修改工单状态");
    }

    Ticket updated = ticketService.updateStatus(id, request.getStatus(), user.userId());

    operationLogService.record(
        user.userId(), "CHANGE_STATUS", id, "工单状态修改为 " + request.getStatus());

    return ApiResponse.success(updated);
  }

  // 分页 / 状态筛选 / 关键词搜索
  @GetMapping("/tickets/page")
  public ApiResponse<IPage<Ticket>> getTicketPage(
      @RequestParam(defaultValue = "1") long current,
      @RequestParam(defaultValue = "10") long size,
      @RequestParam(required = false) TicketStatus status,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) Integer priority,
      @RequestParam(required = false) String username,
      @RequestParam(required = false) Long assigneeId,
      @RequestParam(defaultValue = "false") boolean assignedToMe,
      Authentication authentication) {
    AuthUser user = (AuthUser) authentication.getPrincipal();

    boolean admin = "ADMIN".equals(user.role());

    return ApiResponse.success(
        ticketService.getTicketPage(
            current,
            size,
            status,
            keyword,
            priority,
            username,
            user.userId(),
            admin,
            assigneeId,
            assignedToMe));
  }

  // 工单统计
  @GetMapping("/tickets/statistics")
  public ApiResponse<TicketStatistics> getStatistics(Authentication authentication) {
    AuthUser user = (AuthUser) authentication.getPrincipal();

    boolean admin = "ADMIN".equals(user.role());

    var statistics = ticketService.getStatistics(user.userId(), admin);
    // Knowledge has a separate owner/database in distributed deployments.
    statistics.setArticleCount(articles.countVisible(admin));
    return ApiResponse.success(statistics);
  }

  @PatchMapping("/tickets/{id}/assignee")
  public ApiResponse<Ticket> assign(
      @PathVariable Long id,
      @Valid @RequestBody com.smartticket.backend.dto.AssignmentRequest request,
      Authentication authentication) {
    AuthUser user = (AuthUser) authentication.getPrincipal();
    if (!"ADMIN".equals(user.role()))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有管理员可以指派工单");
    return ApiResponse.success(ticketService.assign(id, request.assigneeId(), user.userId()));
  }
}
