package com.smartticket.backend.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smartticket.backend.enums.TicketStatus;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

@TableName("ticket")
public class Ticket {
  @TableField(value = "created_at", fill = FieldFill.INSERT)
  private LocalDateTime createdAt;

  @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
  private LocalDateTime updatedAt;

  @TableId(type = IdType.AUTO)
  private Long id;

  @NotBlank(message = "标题不能为空")
  @Size(max = 200, message = "标题不能超过200字")
  private String title;

  @Size(max = 1000)
  private String description;

  @NotNull(message = "优先级不能为空")
  @Min(value = 1, message = "优先级必须在1到5之间")
  @Max(value = 5, message = "优先级必须在1到5之间")
  private Integer priority;

  @NotNull private TicketStatus status = TicketStatus.OPEN;

  private Long userId;

  private Long assigneeId;

  public Long getAssigneeId() { return assigneeId; }
  public void setAssigneeId(Long value) { assigneeId = value; }

  @TableField(exist = false)
  private String assigneeUsername;

  public String getAssigneeUsername() { return assigneeUsername; }
  public void setAssigneeUsername(String value) { assigneeUsername = value; }

  @TableField(exist = false)
  private String username;

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public Long getId() {

    return id;
  }

  public void setId(Long id) {

    this.id = id;
  }

  public String getTitle() {

    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public Integer getPriority() {
    return priority;
  }

  public void setPriority(Integer priority) {
    this.priority = priority;
  }

  public TicketStatus getStatus() {
    return status;
  }

  public void setStatus(TicketStatus status) {
    this.status = status;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }
}
