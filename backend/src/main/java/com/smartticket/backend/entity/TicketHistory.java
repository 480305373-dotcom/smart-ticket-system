package com.smartticket.backend.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("ticket_history")
public class TicketHistory {
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

  private Long operatorId;

  public Long getOperatorId() {
    return operatorId;
  }

  public void setOperatorId(Long value) {
    operatorId = value;
  }

  private String action;

  public String getAction() {
    return action;
  }

  public void setAction(String value) {
    action = value;
  }

  private String oldStatus;

  public String getOldStatus() {
    return oldStatus;
  }

  public void setOldStatus(String value) {
    oldStatus = value;
  }

  private String newStatus;

  private Long oldAssigneeId;
  private Long newAssigneeId;
  public Long getOldAssigneeId() { return oldAssigneeId; }
  public void setOldAssigneeId(Long value) { oldAssigneeId = value; }
  public Long getNewAssigneeId() { return newAssigneeId; }
  public void setNewAssigneeId(Long value) { newAssigneeId = value; }

  @TableField(exist = false) private String oldAssigneeUsername;
  @TableField(exist = false) private String newAssigneeUsername;
  public String getOldAssigneeUsername() { return oldAssigneeUsername; }
  public void setOldAssigneeUsername(String value) { oldAssigneeUsername = value; }
  public String getNewAssigneeUsername() { return newAssigneeUsername; }
  public void setNewAssigneeUsername(String value) { newAssigneeUsername = value; }

  public String getNewStatus() {
    return newStatus;
  }

  public void setNewStatus(String value) {
    newStatus = value;
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
