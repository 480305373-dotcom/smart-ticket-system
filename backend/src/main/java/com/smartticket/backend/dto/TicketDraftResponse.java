package com.smartticket.backend.dto;

public class TicketDraftResponse {

    private String title;
    private String description;
    private String suggestion;
    private int recommendedPriority;

    public TicketDraftResponse(
            String title,
            String description,
            String suggestion,
            int recommendedPriority
    ) {
        this.title = title;
        this.description = description;
        this.suggestion = suggestion;
        this.recommendedPriority = recommendedPriority;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public int getRecommendedPriority() {
        return recommendedPriority;
    }
}