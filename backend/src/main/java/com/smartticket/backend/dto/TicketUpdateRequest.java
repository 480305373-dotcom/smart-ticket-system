package com.smartticket.backend.dto;

import com.smartticket.backend.enums.TicketStatus;
import jakarta.validation.constraints.*;

public record TicketUpdateRequest(
    @NotBlank(message = "标题不能为空") @Size(max = 200) String title,
    @Size(max = 1000) String description,
    @NotNull @Min(value = 1, message = "优先级必须在1到5之间") @Max(value = 5, message = "优先级必须在1到5之间")
        Integer priority,
    TicketStatus status) {}
