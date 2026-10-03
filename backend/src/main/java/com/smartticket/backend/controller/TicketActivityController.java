package com.smartticket.backend.controller;

import com.smartticket.backend.dto.*;
import com.smartticket.backend.entity.*;
import com.smartticket.backend.security.AuthUser;
import com.smartticket.backend.service.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/tickets/{id}")
public class TicketActivityController {
  private final TicketService tickets;
  private final TicketActivityService activities;
  private final ArticleService articles;

  public TicketActivityController(TicketService t, TicketActivityService a, ArticleService k) {
    tickets = t;
    activities = a;
    articles = k;
  }

  private Ticket check(Long id, Authentication a) {
    var u = (AuthUser) a.getPrincipal();
    return tickets.getTicketForUser(id, u.userId(), "ADMIN".equals(u.role()));
  }

  @GetMapping("/comments")
  public ApiResponse<List<TicketComment>> comments(@PathVariable Long id, Authentication a) {
    check(id, a);
    return ApiResponse.success(activities.comments(id));
  }

  @PostMapping("/comments")
  public ApiResponse<TicketComment> comment(
      @PathVariable Long id, @Valid @RequestBody CommentRequest r, Authentication a) {
    check(id, a);
    return ApiResponse.success(
        activities.comment(id, ((AuthUser) a.getPrincipal()).userId(), r.content()));
  }

  @GetMapping("/history")
  public ApiResponse<List<TicketHistory>> history(@PathVariable Long id, Authentication a) {
    check(id, a);
    return ApiResponse.success(activities.history(id));
  }

  @GetMapping("/knowledge-recommendations")
  public ApiResponse<List<KnowledgeArticle>> recommend(@PathVariable Long id, Authentication a) {
    return ApiResponse.success(articles.recommend(check(id, a)));
  }
}
