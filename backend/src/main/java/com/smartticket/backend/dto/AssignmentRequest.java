package com.smartticket.backend.dto;

import jakarta.validation.constraints.Min;

/** A null assignee explicitly clears the assignment. Only ADMIN users can handle tickets. */
public record AssignmentRequest(@Min(value = 1, message = "处理人ID必须大于0") Long assigneeId) {}
