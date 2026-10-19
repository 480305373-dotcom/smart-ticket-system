package com.smartticket.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartticket.backend.dto.ArticleRequest;
import com.smartticket.backend.entity.*;
import com.smartticket.backend.mapper.KnowledgeArticleMapper;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "app.knowledge.remote", havingValue = "false", matchIfMissing = true)
public class ArticleService {
  private final KnowledgeArticleMapper mapper;

  public ArticleService(KnowledgeArticleMapper mapper) {
    this.mapper = mapper;
  }

  public IPage<KnowledgeArticle> page(
      long current, long size, String title, String category, String keyword, boolean admin) {
    TicketService.validatePage(current, size);
    var w = new LambdaQueryWrapper<KnowledgeArticle>();
    if (!admin) w.eq(KnowledgeArticle::getStatus, "PUBLISHED");
    if (StringUtils.hasText(title)) w.like(KnowledgeArticle::getTitle, title.trim());
    if (StringUtils.hasText(category)) w.eq(KnowledgeArticle::getCategory, category);
    if (StringUtils.hasText(keyword))
      w.and(
          q ->
              q.like(KnowledgeArticle::getTitle, keyword)
                  .or()
                  .like(KnowledgeArticle::getKeywords, keyword)
                  .or()
                  .like(KnowledgeArticle::getSummary, keyword));
    return mapper.selectPage(
        new Page<>(current, size),
        w.orderByDesc(KnowledgeArticle::getUpdatedAt, KnowledgeArticle::getId));
  }

  public KnowledgeArticle get(Long id, boolean admin) {
    var a = mapper.selectById(id);
    if (a == null || (!admin && !"PUBLISHED".equals(a.getStatus())))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文章不存在或尚未发布");
    return a;
  }

  @org.springframework.cache.annotation.CacheEvict(value = "ticketStatistics", allEntries = true)
  public KnowledgeArticle save(Long id, ArticleRequest r, Long operator) {
    var a = id == null ? new KnowledgeArticle() : get(id, true);
    a.setTitle(r.title().trim());
    a.setSummary(r.summary().trim());
    a.setContent(r.content());
    a.setCategory(r.category().trim());
    a.setKeywords(r.keywords());
    a.setStatus(r.status());
    if (id == null) {
      a.setCreatedBy(operator);
      mapper.insert(a);
    } else mapper.updateById(a);
    return get(a.getId(), true);
  }

  @org.springframework.cache.annotation.CacheEvict(value = "ticketStatistics", allEntries = true)
  public void delete(Long id) {
    get(id, true);
    mapper.deleteById(id);
  }

  public List<String> categories(boolean admin) {
    var w = new LambdaQueryWrapper<KnowledgeArticle>();
    if (!admin) w.eq(KnowledgeArticle::getStatus, "PUBLISHED");
    return mapper.selectList(w.select(KnowledgeArticle::getCategory)).stream()
        .map(KnowledgeArticle::getCategory)
        .distinct()
        .sorted()
        .toList();
  }

  public long countVisible(boolean admin) {
    var query = new LambdaQueryWrapper<KnowledgeArticle>();
    if (!admin) query.eq(KnowledgeArticle::getStatus, "PUBLISHED");
    return mapper.selectCount(query);
  }

  // Deterministic keyword matching: no model, embedding or invented relevance.
  public int score(Ticket ticket, KnowledgeArticle article) {
    String text =
        (ticket.getTitle() + " " + Objects.toString(ticket.getDescription(), ""))
            .toLowerCase(Locale.ROOT);
    Set<String> terms =
        new HashSet<>(
            Arrays.asList(article.getKeywords().toLowerCase(Locale.ROOT).split("[,，;；\\s]+")));
    terms.add(article.getCategory().toLowerCase(Locale.ROOT));
    int score = 0;
    for (String term : terms) if (term.length() >= 2 && text.contains(term)) score += 3;
    if (text.contains(article.getTitle().toLowerCase(Locale.ROOT))) score += 5;
    return score;
  }

  private record Ranked(KnowledgeArticle article, int score) {}

  public List<KnowledgeArticle> recommend(Ticket ticket) {
    // Score small metadata rows once; load full content only for the three selected articles.
    var candidates =
        mapper.selectList(
            new LambdaQueryWrapper<KnowledgeArticle>()
                .select(
                    KnowledgeArticle::getId,
                    KnowledgeArticle::getTitle,
                    KnowledgeArticle::getCategory,
                    KnowledgeArticle::getKeywords)
                .eq(KnowledgeArticle::getStatus, "PUBLISHED"));
    var ranked =
        candidates.stream()
            .map(a -> new Ranked(a, score(ticket, a)))
            .filter(r -> r.score() > 0)
            .sorted(
                Comparator.comparingInt(Ranked::score)
                    .reversed()
                    .thenComparing(r -> r.article().getId()))
            .limit(3)
            .toList();
    if (ranked.isEmpty()) return List.of();
    var ids = ranked.stream().map(r -> r.article().getId()).toList();
    var selected =
        mapper.selectList(
            new LambdaQueryWrapper<KnowledgeArticle>()
                .in(KnowledgeArticle::getId, ids)
                .eq(KnowledgeArticle::getStatus, "PUBLISHED"));
    var byId =
        selected.stream()
            .collect(java.util.stream.Collectors.toMap(KnowledgeArticle::getId, a -> a));
    return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
  }
}
