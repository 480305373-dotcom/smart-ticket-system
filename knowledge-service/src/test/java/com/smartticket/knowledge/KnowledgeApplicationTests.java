package com.smartticket.knowledge;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.net.http.*;
import java.sql.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Real service context + PostgreSQL in a uniquely owned, disposable schema. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties={"eureka.client.enabled=false","app.internal-key=integration-test-key-at-least-thirty-two-characters"})
@EnabledIfSystemProperty(named="infra.tests",matches="true")
class KnowledgeApplicationTests {
  private static final String SCHEMA="knowledge_test_"+UUID.randomUUID().toString().replace("-","");
  private static final String URL=System.getenv().getOrDefault("ENTERPRISE_DB_URL","jdbc:postgresql://localhost:5432/intelligent_ticket");
  private static final String USER=System.getenv().getOrDefault("ENTERPRISE_DB_USER","postgres");
  private static final String PASSWORD=System.getenv().getOrDefault("ENTERPRISE_DB_PASSWORD",System.getenv().getOrDefault("DB_PASSWORD",""));
  @Autowired Environment environment;
  @DynamicPropertySource static void database(DynamicPropertyRegistry registry)throws Exception {
    try(var connection=DriverManager.getConnection(URL,USER,PASSWORD);var statement=connection.createStatement()) {
      statement.execute("CREATE SCHEMA "+SCHEMA);
    }
    registry.add("spring.datasource.url",()->URL+(URL.contains("?")?"&":"?")+"currentSchema="+SCHEMA);
    registry.add("spring.datasource.username",()->USER);registry.add("spring.datasource.password",()->PASSWORD);
  }
  private HttpResponse<String> call(String path,String key,String body)throws Exception {
    var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+environment.getRequiredProperty("local.server.port")+path));
    if(key!=null)builder.header("X-Service-Key",key);
    if(body==null)builder.GET();else builder.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body));
    return HttpClient.newHttpClient().send(builder.build(),HttpResponse.BodyHandlers.ofString());
  }
  @Test void contextWiresMybatisAndInternalCredentialBoundary()throws Exception {
    assertEquals(401,call("/knowledge",null,null).statusCode());
    assertEquals(403,call("/internal/knowledge/count","forged","{\"admin\":true}").statusCode());
    var result=call("/internal/knowledge/count","integration-test-key-at-least-thirty-two-characters","{\"admin\":true}");
    assertEquals(200,result.statusCode());assertTrue(result.body().contains("\"data\":0"));
  }
  @Test void readinessDoesNotRequireAccessToCoreDatabase()throws Exception {
    assertEquals(200,call("/actuator/health/readiness",null,null).statusCode());
  }
  @AfterAll static void cleanup()throws Exception {
    if(!SCHEMA.matches("knowledge_test_[0-9a-f]{32}"))throw new IllegalStateException("Invalid test schema");
    try(var connection=DriverManager.getConnection(URL,USER,PASSWORD);var statement=connection.createStatement()) {
      statement.execute("DROP SCHEMA IF EXISTS "+SCHEMA+" CASCADE");
    }
  }
}
