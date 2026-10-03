package com.smartticket.backend.entity;

/** Durable request key; inserting it and the ticket happens in the same transaction. */
public class TicketRequest {
  private Long userId;
  private String requestId;
  private String fingerprint;
  private Long ticketId;
  public Long getUserId() { return userId; }
  public void setUserId(Long value) { userId = value; }
  public String getRequestId() { return requestId; }
  public void setRequestId(String value) { requestId = value; }
  public String getFingerprint() { return fingerprint; }
  public void setFingerprint(String value) { fingerprint = value; }
  public Long getTicketId() { return ticketId; }
  public void setTicketId(Long value) { ticketId = value; }
}
