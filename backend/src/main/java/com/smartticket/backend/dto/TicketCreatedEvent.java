package com.smartticket.backend.dto;

import java.util.UUID;

public class TicketCreatedEvent {

    private String eventId;
    private Long userId;
    private Long ticketId;
    private String source;

    public TicketCreatedEvent() {
    }

    public TicketCreatedEvent(
            Long userId,
            Long ticketId,
            String source
    ) {
        this.eventId = UUID.randomUUID().toString();
        this.userId = userId;
        this.ticketId = ticketId;
        this.source = source;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}