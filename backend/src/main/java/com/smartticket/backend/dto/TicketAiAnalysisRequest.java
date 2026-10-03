package com.smartticket.backend.dto;

import jakarta.validation.constraints.NotBlank;

public class TicketAiAnalysisRequest {

    @NotBlank
    private String description;

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}