package com.smartticket.backend.dto;

public class TicketAiAnalysisResponse {

    private String suggestion;
    private int recommendedPriority;

    public TicketAiAnalysisResponse(
            String suggestion,
            int recommendedPriority
    ) {
        this.suggestion = suggestion;
        this.recommendedPriority = recommendedPriority;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public int getRecommendedPriority() {
        return recommendedPriority;
    }
}