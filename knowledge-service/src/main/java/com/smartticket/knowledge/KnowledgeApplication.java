package com.smartticket.knowledge;

import com.smartticket.backend.config.*;
import com.smartticket.backend.controller.KnowledgeController;
import com.smartticket.backend.exception.GlobalExceptionHandler;
import com.smartticket.backend.mapper.KnowledgeArticleMapper;
import com.smartticket.backend.service.ArticleService;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.*;

/** Imports the existing tested knowledge domain, not the core component scan. */
@SpringBootApplication
@Import({ArticleService.class, KnowledgeController.class, MyBatisPlusConfig.class,
    MyMetaObjectHandler.class, GlobalExceptionHandler.class, ServiceClientConfig.class})
public class KnowledgeApplication {
  public static void main(String[] args) { SpringApplication.run(KnowledgeApplication.class,args); }
  @Bean MapperFactoryBean<KnowledgeArticleMapper> articleMapper() {
    return new MapperFactoryBean<>(KnowledgeArticleMapper.class);
  }
}
