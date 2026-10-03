package com.smartticket.backend.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.smartticket.backend.dto.*;
import com.smartticket.backend.entity.KnowledgeArticle;
import com.smartticket.backend.security.AuthUser;
import com.smartticket.backend.service.ArticleService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/knowledge")
public class KnowledgeController {
  private final ArticleService service;

  public KnowledgeController(ArticleService service) {
    this.service = service;
  }

  private AuthUser user(Authentication a) {
    return (AuthUser) a.getPrincipal();
  }

  private boolean admin(Authentication a) {
    return "ADMIN".equals(user(a).role());
  }

  private void requireAdmin(Authentication a) {
    if (!admin(a)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有管理员可以管理知识文章");
  }

  @GetMapping
  public ApiResponse<IPage<KnowledgeArticle>> page(
      @RequestParam(defaultValue = "1") long current,
      @RequestParam(defaultValue = "10") long size,
      @RequestParam(required = false) String title,
      @RequestParam(required = false) String category,
      @RequestParam(required = false) String keyword,
      Authentication a) {
    return ApiResponse.success(service.page(current, size, title, category, keyword, admin(a)));
  }

  @GetMapping("/categories")
  public ApiResponse<List<String>> categories(Authentication a) {
    return ApiResponse.success(service.categories(admin(a)));
  }

  @GetMapping("/{id}")
  public ApiResponse<KnowledgeArticle> get(@PathVariable Long id, Authentication a) {
    return ApiResponse.success(service.get(id, admin(a)));
  }

  @PostMapping
  public ApiResponse<KnowledgeArticle> create(
      @Valid @RequestBody ArticleRequest r, Authentication a) {
    requireAdmin(a);
    return ApiResponse.success(service.save(null, r, user(a).userId()));
  }

  @PutMapping("/{id}")
  public ApiResponse<KnowledgeArticle> update(
      @PathVariable Long id, @Valid @RequestBody ArticleRequest r, Authentication a) {
    requireAdmin(a);
    return ApiResponse.success(service.save(id, r, user(a).userId()));
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Void> delete(@PathVariable Long id, Authentication a) {
    requireAdmin(a);
    service.delete(id);
    return ApiResponse.success(null);
  }
}
