package com.smartticket.knowledge;

import com.smartticket.backend.dto.*;
import com.smartticket.backend.entity.Ticket;
import com.smartticket.backend.service.ArticleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/knowledge")
public class InternalKnowledgeController {
  private final ArticleService articles;
  public InternalKnowledgeController(ArticleService articles) {this.articles=articles;}
  public record PageRequest(@Min(1) long current,@Min(1) @Max(100) long size,String title,String category,String keyword,boolean admin) {}
  public record Lookup(@NotNull Long id,boolean admin) {}
  public record Scope(boolean admin) {}
  public record Save(Long id,@NotNull @Valid ArticleRequest article,@NotNull Long operator) {}
  @PostMapping("/page") public ApiResponse<?> page(@Valid @RequestBody PageRequest r) {
    return ApiResponse.success(articles.page(r.current(),r.size(),r.title(),r.category(),r.keyword(),r.admin()));
  }
  @PostMapping("/get") public ApiResponse<?> get(@Valid @RequestBody Lookup r) {return ApiResponse.success(articles.get(r.id(),r.admin()));}
  @PostMapping("/save") public ApiResponse<?> save(@Valid @RequestBody Save r) {return ApiResponse.success(articles.save(r.id(),r.article(),r.operator()));}
  @PostMapping("/delete") public ApiResponse<?> delete(@Valid @RequestBody Lookup r) {articles.delete(r.id());return ApiResponse.success(null);}
  @PostMapping("/categories") public ApiResponse<?> categories(@RequestBody Scope r) {return ApiResponse.success(articles.categories(r.admin()));}
  @PostMapping("/count") public ApiResponse<?> count(@RequestBody Scope r) {return ApiResponse.success(articles.countVisible(r.admin()));}
  @PostMapping("/recommend") public ApiResponse<?> recommend(@RequestBody Ticket ticket) {return ApiResponse.success(articles.recommend(ticket));}
}
