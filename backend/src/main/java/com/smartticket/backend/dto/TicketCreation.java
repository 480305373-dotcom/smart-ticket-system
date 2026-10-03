package com.smartticket.backend.dto;

import com.smartticket.backend.entity.Ticket;

public record TicketCreation(Ticket ticket, boolean created) {}
