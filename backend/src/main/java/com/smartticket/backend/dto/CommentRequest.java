package com.smartticket.backend.dto;

import jakarta.validation.constraints.*;

public record CommentRequest(
    @NotBlank(message = "留言不能为空") @Size(max = 2000, message = "留言不能超过2000字") String content) {}
