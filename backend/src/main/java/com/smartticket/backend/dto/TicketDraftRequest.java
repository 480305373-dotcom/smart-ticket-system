package com.smartticket.backend.dto;

import jakarta.validation.constraints.NotBlank;

public class TicketDraftRequest {

    @NotBlank
    private String description;

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}