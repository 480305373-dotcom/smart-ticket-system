package com.smartticket.backend.service;

import com.smartticket.backend.dto.KnowledgeRequest;
import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "app.ai.enabled",
    havingValue = "true")
@Service
public class KnowledgeService {

  private final VectorStore vectorStore;
  private final ArticleService articles;
  private final PublishedKnowledgeIndex index;

  public KnowledgeService(
      VectorStore vectorStore, ArticleService articles, PublishedKnowledgeIndex index) {
    this.vectorStore = vectorStore;
    this.articles = articles;
    this.index = index;
  }

  public void addKnowledge(KnowledgeRequest request) {

    var auth =
        (com.smartticket.backend.security.AuthUser)
            org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
    articles.save(
        null,
        new com.smartticket.backend.dto.ArticleRequest(
            request.getTitle(),
            request.getContent().substring(0, Math.min(300, request.getContent().length())),
            request.getContent(),
            "企业知识",
            request.getTitle(),
            "PUBLISHED"),
        auth.userId());
    index.sync();
  }

  public List<Document> search(String question) {

    var found =
        vectorStore.similaritySearch(
            SearchRequest.builder()
                .query(question)
                .topK(3)
                .filterExpression("source == 'published_article'")
                .build());
    var safe = new java.util.ArrayList<Document>();
    for (var doc : found)
      try {
        var a = articles.get(Long.valueOf(doc.getMetadata().get("article_id").toString()), false);
        if (PublishedKnowledgeIndex.fingerprint(a).equals(doc.getMetadata().get("fingerprint")))
          safe.add(new Document(a.getTitle() + "\n" + a.getContent(), doc.getMetadata()));
      } catch (org.springframework.web.server.ResponseStatusException | NumberFormatException e) {
        /* Never expose deleted or unpublished knowledge. */
      }
    return safe;
  }
}
