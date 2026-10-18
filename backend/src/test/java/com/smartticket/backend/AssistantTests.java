package com.smartticket.backend;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.smartticket.backend.controller.AssistantController;
import com.smartticket.backend.dto.AiChatRequest;
import com.smartticket.backend.entity.KnowledgeArticle;
import com.smartticket.backend.service.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

class AssistantTests {
  @Test
  void unpublishedAndStaleSourcesNeverReachModel() {
    var vectors = mock(VectorStore.class);
    var articles = mock(ArticleService.class);
    var index = mock(PublishedKnowledgeIndex.class);
    var builder = mock(ChatClient.Builder.class, RETURNS_SELF);
    var chat = mock(ChatClient.class);
    when(builder.build()).thenReturn(chat);
    var doc =
        Document.builder()
            .text("不允许进入提示词的草稿")
            .metadata(Map.of("article_id", "999", "fingerprint", "old-version"))
            .build();
    when(vectors.similaritySearch(any(org.springframework.ai.vectorstore.SearchRequest.class)))
        .thenReturn(List.of(doc));
    when(articles.get(999L, false))
        .thenThrow(
            new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND));
    var request = new AiChatRequest();
    request.setMessage("测试问题");
    var controller = new AssistantController(new AiTextService(builder), vectors, articles, index);
    Map<?, ?> response = (Map<?, ?>) controller.answer(request).getData();
    assertEquals(List.of(), response.get("sources"));
    verifyNoInteractions(chat);
    var published = new KnowledgeArticle();
    published.setTitle("已变更文章");
    published.setContent("新正文");
    doReturn(published).when(articles).get(999L, false);
    response = (Map<?, ?>) controller.answer(request).getData();
    assertEquals(List.of(), response.get("sources"));
    verifyNoInteractions(chat);
  }

  @Test
  void emptyModelOutputIsNotReportedAsSuccessfulAnswer() {
    var vectors = mock(VectorStore.class);
    var articles = mock(ArticleService.class);
    var index = mock(PublishedKnowledgeIndex.class);
    var builder = mock(ChatClient.Builder.class, RETURNS_SELF);
    var chat = mock(ChatClient.class, RETURNS_DEEP_STUBS);
    when(builder.build()).thenReturn(chat);
    var a = new KnowledgeArticle();
    a.setId(1L);
    a.setTitle("VPN指南");
    a.setContent("检查账号权限");
    when(articles.get(1L, false)).thenReturn(a);
    var doc =
        Document.builder()
            .text(a.getContent())
            .metadata(
                Map.of("article_id", "1", "fingerprint", PublishedKnowledgeIndex.fingerprint(a)))
            .build();
    when(vectors.similaritySearch(any(org.springframework.ai.vectorstore.SearchRequest.class)))
        .thenReturn(List.of(doc));
    when(chat.prompt().system(anyString()).user(anyString()).call().content()).thenReturn("");
    var request = new AiChatRequest();
    request.setMessage("VPN认证失败");
    var controller = new AssistantController(new AiTextService(builder), vectors, articles, index);
    var error =
        assertThrows(
            org.springframework.web.server.ResponseStatusException.class,
            () -> controller.answer(request));
    assertEquals(503, error.getStatusCode().value());
  }
}
