package com.smartticket.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartticket.backend.entity.KnowledgeArticle;
import com.smartticket.backend.mapper.KnowledgeArticleMapper;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.ai.enabled", havingValue = "true")
public class PublishedKnowledgeIndex {
  private final VectorStore vectors;
  private final KnowledgeArticleMapper articles;
  private final JdbcTemplate jdbc;
  private final AtomicBoolean running = new AtomicBoolean();
  private volatile Map<String, Object> state =
      Map.of("running", false, "indexed", 0, "message", "等待首次同步");

  public PublishedKnowledgeIndex(
      VectorStore vectors, KnowledgeArticleMapper articles, JdbcTemplate jdbc) {
    this.vectors = vectors;
    this.articles = articles;
    this.jdbc = jdbc;
  }

  public static String fingerprint(KnowledgeArticle a) {
    return RateLimitService.digest(a.getTitle() + "\n" + a.getContent());
  }

  public Map<String, Object> state() {
    return state;
  }

  @Scheduled(fixedDelayString = "${app.ai.index-interval-ms:60000}", initialDelay = 20000)
  public void scheduled() {
    try {
      sync();
    } catch (Exception e) {
      org.slf4j.LoggerFactory.getLogger(getClass())
          .warn("Knowledge indexing deferred: {}", e.getClass().getSimpleName());
    }
  }

  public Map<String, Object> sync() {
    if (!running.compareAndSet(false, true)) return state;
    int count = 0;
    state = Map.of("running", true, "indexed", 0, "message", "正在同步已发布知识");
    try {
      var published =
          articles.selectList(
              new LambdaQueryWrapper<KnowledgeArticle>()
                  .eq(KnowledgeArticle::getStatus, "PUBLISHED"));
      for (var a : published) {
        String hash = fingerprint(a);
        var old =
            jdbc.queryForList(
                "SELECT fingerprint FROM knowledge_vector_index WHERE article_id=?",
                String.class,
                a.getId());
        if (!old.isEmpty() && hash.equals(old.getFirst())) continue;
        String text = a.getTitle() + "\n" + a.getContent();
        var docs = new ArrayList<Document>();
        for (int start = 0, part = 0; start < text.length(); start += 700, part++) {
          String id =
              UUID.nameUUIDFromBytes(
                      ("smartticket:article:" + a.getId() + ":" + part)
                          .getBytes(StandardCharsets.UTF_8))
                  .toString();
          docs.add(
              Document.builder()
                  .id(id)
                  .text(text.substring(start, Math.min(start + 800, text.length())))
                  .metadata(
                      Map.of(
                          "source",
                          "published_article",
                          "article_id",
                          a.getId().toString(),
                          "fingerprint",
                          hash,
                          "title",
                          a.getTitle()))
                  .build());
        }
        vectors.delete("source == 'published_article' && article_id == '" + a.getId() + "'");
        vectors.add(docs);
        jdbc.update(
            "INSERT INTO knowledge_vector_index(article_id,fingerprint) VALUES (?,?) ON"
                + " CONFLICT(article_id) DO UPDATE SET"
                + " fingerprint=EXCLUDED.fingerprint,indexed_at=CURRENT_TIMESTAMP",
            a.getId(),
            hash);
        count++;
        state = Map.of("running", true, "indexed", count, "message", "正在同步已发布知识");
      }
      state =
          Map.of(
              "running",
              false,
              "indexed",
              count,
              "message",
              "同步完成",
              "lastSync",
              java.time.Instant.now().toString());
      return state;
    } catch (RuntimeException e) {
      state = Map.of("running", false, "indexed", count, "message", "同步失败，请检查模型服务");
      throw e;
    } finally {
      running.set(false);
    }
  }
}
