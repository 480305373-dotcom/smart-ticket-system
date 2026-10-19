package com.smartticket.backend.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartticket.backend.dto.ArticleRequest;
import com.smartticket.backend.entity.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

/** Service boundary: the core has no credentials to the knowledge database. */
@Service
@ConditionalOnProperty(name="app.knowledge.remote", havingValue="true")
public class RemoteArticleService extends ArticleService {
  private final RestClient client;
  private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
  public RemoteArticleService(@org.springframework.beans.factory.annotation.Qualifier("serviceRestClient") RestClient.Builder serviceRestClient, @Value("${app.internal-key}") String key) {
    super(null);
    client = serviceRestClient.baseUrl("http://smartticket-knowledge/internal/knowledge")
        .defaultHeader("X-Service-Key", key).build();
  }
  private Object call(String path, Object body) {
    try {
      var response = client.post().uri(path).body(body).retrieve().body(Map.class);
      return Objects.requireNonNull(response).get("data");
    } catch (RestClientResponseException e) {
      throw new ResponseStatusException(HttpStatus.valueOf(e.getStatusCode().value()), "知识服务请求失败");
    } catch (RestClientException e) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "知识服务暂时不可用");
    }
  }
  @Override public IPage<KnowledgeArticle> page(long current,long size,String title,String category,String keyword,boolean admin) {
    TicketService.validatePage(current,size);
    var request = new HashMap<String,Object>();
    request.put("current",current); request.put("size",size); request.put("title",title);
    request.put("category",category); request.put("keyword",keyword); request.put("admin",admin);
    var result = (Map<?,?>) call("/page",request);
    var page = new Page<KnowledgeArticle>(current,size,((Number)result.get("total")).longValue());
    page.setRecords(json.convertValue(result.get("records"),json.getTypeFactory().constructCollectionType(List.class,KnowledgeArticle.class)));
    return page;
  }
  @Override public KnowledgeArticle get(Long id,boolean admin) {
    return json.convertValue(call("/get",Map.of("id",id,"admin",admin)),KnowledgeArticle.class);
  }
  @Override public KnowledgeArticle save(Long id,ArticleRequest request,Long operator) {
    var body = new HashMap<String,Object>(); body.put("id",id);body.put("article",request);body.put("operator",operator);
    return json.convertValue(call("/save",body),KnowledgeArticle.class);
  }
  @Override public void delete(Long id) { call("/delete",Map.of("id",id)); }
  @Override public List<String> categories(boolean admin) {
    return json.convertValue(call("/categories",Map.of("admin",admin)),json.getTypeFactory().constructCollectionType(List.class,String.class));
  }
  @Override public long countVisible(boolean admin) { return ((Number)call("/count",Map.of("admin",admin))).longValue(); }
  @Override public List<KnowledgeArticle> recommend(Ticket ticket) {
    return json.convertValue(call("/recommend",ticket),json.getTypeFactory().constructCollectionType(List.class,KnowledgeArticle.class));
  }
}
