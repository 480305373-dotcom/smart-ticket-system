package com.smartticket.backend.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("ticket_comment")
public class TicketComment {
  @TableId(type = IdType.AUTO)
  private Long id;

  public Long getId() {
    return id;
  }

  public void setId(Long value) {
    id = value;
  }

  private Long ticketId;

  public Long getTicketId() {
    return ticketId;
  }

  public void setTicketId(Long value) {
    ticketId = value;
  }

  private Long userId;

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long value) {
    userId = value;
  }

  private String content;

  public String getContent() {
    return content;
  }

  public void setContent(String value) {
    content = value;
  }

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createdAt;

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime value) {
    createdAt = value;
  }

  @TableField(exist = false)
  private String username;

  public String getUsername() {
    return username;
  }

  public void setUsername(String value) {
    username = value;
  }
}
