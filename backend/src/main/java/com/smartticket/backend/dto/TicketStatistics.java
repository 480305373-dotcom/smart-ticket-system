package com.smartticket.backend.dto;

import java.io.Serializable;

public class TicketStatistics implements Serializable {

  private static final long serialVersionUID = 1L;

  private long highPriority, userCount, articleCount;

  public long getHighPriority() {
    return highPriority;
  }

  public void setHighPriority(long v) {
    highPriority = v;
  }

  public long getUserCount() {
    return userCount;
  }

  public void setUserCount(long v) {
    userCount = v;
  }

  public long getArticleCount() {
    return articleCount;
  }

  public void setArticleCount(long v) {
    articleCount = v;
  }

  private long total;
  private long open;
  private long inProgress;
  private long resolved;
  private long closed;

  public TicketStatistics() {}

  public TicketStatistics(long total, long open, long inProgress, long resolved, long closed) {
    this.total = total;
    this.open = open;
    this.inProgress = inProgress;
    this.resolved = resolved;
    this.closed = closed;
  }

  public long getTotal() {
    return total;
  }

  public void setTotal(long total) {
    this.total = total;
  }

  public long getOpen() {
    return open;
  }

  public void setOpen(long open) {
    this.open = open;
  }

  public long getInProgress() {
    return inProgress;
  }

  public void setInProgress(long inProgress) {
    this.inProgress = inProgress;
  }

  public long getResolved() {
    return resolved;
  }

  public void setResolved(long resolved) {
    this.resolved = resolved;
  }

  public long getClosed() {
    return closed;
  }

  public void setClosed(long closed) {
    this.closed = closed;
  }
}
