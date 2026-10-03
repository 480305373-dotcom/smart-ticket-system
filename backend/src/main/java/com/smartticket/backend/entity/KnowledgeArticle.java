package com.smartticket.backend.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("knowledge_article")
public class KnowledgeArticle {
  @TableId(type = IdType.AUTO)
  private Long id;

  public Long getId() {
    return id;
  }

  public void setId(Long value) {
    id = value;
  }

  private String title;

  public String getTitle() {
    return title;
  }

  public void setTitle(String value) {
    title = value;
  }

  private String summary;

  public String getSummary() {
    return summary;
  }

  public void setSummary(String value) {
    summary = value;
  }

  private String content;

  public String getContent() {
    return content;
  }

  public void setContent(String value) {
    content = value;
  }

  private String category;

  public String getCategory() {
    return category;
  }

  public void setCategory(String value) {
    category = value;
  }

  private String keywords;

  public String getKeywords() {
    return keywords;
  }

  public void setKeywords(String value) {
    keywords = value;
  }

  private String status;

  public String getStatus() {
    return status;
  }

  public void setStatus(String value) {
    status = value;
  }

  private Long createdBy;

  public Long getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(Long value) {
    createdBy = value;
  }

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createdAt;

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime value) {
    createdAt = value;
  }

  @TableField(fill = FieldFill.INSERT_UPDATE)
  private LocalDateTime updatedAt;

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime value) {
    updatedAt = value;
  }
}
