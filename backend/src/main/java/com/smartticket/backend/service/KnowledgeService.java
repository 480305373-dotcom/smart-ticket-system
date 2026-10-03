package com.smartticket.backend.service;

import com.smartticket.backend.dto.KnowledgeRequest;
import java.util.List;
import java.util.Map;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@org.springframework.context.annotation.Profile("!dev")
@Service
public class KnowledgeService {

  private final VectorStore vectorStore;

  public KnowledgeService(VectorStore vectorStore) {
    this.vectorStore = vectorStore;
  }

  public void addKnowledge(KnowledgeRequest request) {

    Document document =
        new Document(
            request.getContent(), Map.of("title", request.getTitle(), "type", "knowledge"));

    vectorStore.add(List.of(document));
  }

  public List<Document> search(String question) {

    return vectorStore.similaritySearch(SearchRequest.builder().query(question).topK(3).build());
  }
}
