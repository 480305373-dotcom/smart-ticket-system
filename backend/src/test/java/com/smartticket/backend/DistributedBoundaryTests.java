package com.smartticket.backend;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartticket.backend.config.RabbitMqConfig;
import com.smartticket.backend.controller.IdentityController;
import com.smartticket.backend.entity.KnowledgeArticle;
import com.smartticket.backend.security.AuthUser;
import com.smartticket.backend.service.*;
import java.sql.*;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.ai.vectorstore.VectorStore;

class DistributedBoundaryTests {
  private static final String KEY="test-internal-key-at-least-thirty-two-characters";
  @Test void remotePagePreservesCountsAndDates() {
    var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();
    var remote=new RemoteArticleService(builder,KEY);
    server.expect(requestTo("http://smartticket-knowledge/internal/knowledge/page"))
      .andExpect(header("X-Service-Key",KEY))
      .andRespond(withSuccess("""
        {"code":200,"data":{"total":1,"records":[{"id":7,"title":"VPN","createdAt":"2026-10-19T12:00:00"}]}}
        """,MediaType.APPLICATION_JSON));
    var page=remote.page(1,10,null,null,null,false);
    assertEquals(1,page.getTotal());assertEquals(7L,page.getRecords().getFirst().getId());
    assertEquals(2026,page.getRecords().getFirst().getCreatedAt().getYear());server.verify();
  }
  @Test void remoteFailureNeverFallsBackToLocalTables() {
    var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();
    var remote=new RemoteArticleService(builder,KEY);
    server.expect(anything()).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
    assertEquals(503,assertThrows(ResponseStatusException.class,()->remote.countVisible(false)).getStatusCode().value());
    server.verify();
  }
  @Test void remoteHiddenSourceRemainsNotFound() {
    var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();
    var remote=new RemoteArticleService(builder,KEY);
    server.expect(anything()).andRespond(withStatus(HttpStatus.NOT_FOUND));
    assertEquals(404,assertThrows(ResponseStatusException.class,()->remote.get(1L,false)).getStatusCode().value());
    server.verify();
  }
  @Test void identityRequiresInternalCredentialAndAuthenticatedPrincipal() {
    var controller=new IdentityController(KEY);var principal=new AuthUser(7L,"employee","USER");
    var auth=UsernamePasswordAuthenticationToken.authenticated(principal,null,List.of());
    assertEquals(principal,controller.identity(KEY,auth).getData());
    assertEquals(403,assertThrows(ResponseStatusException.class,()->controller.identity("forged",auth)).getStatusCode().value());
    assertThrows(IllegalArgumentException.class,()->new IdentityController("short"));
  }
  @Test void quorumQueuesRequestThreeMembersWithoutChangingStandaloneQueues() {
    var config=new RabbitMqConfig();ReflectionTestUtils.setField(config,"queueType","quorum");
    assertEquals("quorum",config.ticketAuditQueue().getArguments().get("x-queue-type"));
    assertEquals(3,config.ticketAuditDeadLetterQueue().getArguments().get("x-quorum-initial-group-size"));
    ReflectionTestUtils.setField(config,"queueType","classic");
    assertFalse(config.ticketAuditQueue().getArguments().containsKey("x-queue-type"));
  }
  @Test void indexSkipsWhenAnotherInstanceOwnsDatabaseLock() throws Exception {
    var source=mock(DataSource.class);var connection=mock(Connection.class);var stmt=mock(PreparedStatement.class);var result=mock(ResultSet.class);
    when(source.getConnection()).thenReturn(connection);when(connection.prepareStatement(anyString())).thenReturn(stmt);
    when(stmt.executeQuery()).thenReturn(result);when(result.next()).thenReturn(true);when(result.getBoolean(1)).thenReturn(false);
    var vectors=mock(VectorStore.class);var articles=mock(ArticleService.class);
    var index=new PublishedKnowledgeIndex(vectors,articles,new JdbcTemplate(source));
    assertEquals("另一实例正在同步",index.sync().get("message"));
    verifyNoInteractions(vectors,articles);verify(connection).close();
  }
  @Test void failedIndexReleasesDatabaseLockAndCanRetry() throws Exception {
    var source=mock(DataSource.class);var connection=mock(Connection.class);var lock=mock(PreparedStatement.class);var unlock=mock(PreparedStatement.class);var result=mock(ResultSet.class);
    when(source.getConnection()).thenReturn(connection);
    when(connection.prepareStatement("SELECT pg_try_advisory_lock(741852966)")).thenReturn(lock);
    when(connection.prepareStatement("SELECT pg_advisory_unlock(741852966)")).thenReturn(unlock);
    when(lock.executeQuery()).thenReturn(result);when(result.next()).thenReturn(true);when(result.getBoolean(1)).thenReturn(true);
    var articles=mock(ArticleService.class);when(articles.page(1,100,null,null,null,false)).thenThrow(new IllegalStateException("unavailable"));
    var index=new PublishedKnowledgeIndex(mock(VectorStore.class),articles,new JdbcTemplate(source));
    assertThrows(IllegalStateException.class,index::sync);verify(unlock).execute();
    doReturn(new Page<KnowledgeArticle>(1,100,0)).when(articles).page(1,100,null,null,null,false);
    assertEquals("同步完成",index.sync().get("message"));verify(unlock,times(2)).execute();
  }
}
