package com.smartticket.backend.dto;

import jakarta.validation.constraints.NotBlank;

public class AiChatRequest {

  @NotBlank
  @jakarta.validation.constraints.Size(max = 2000)
  private String message;

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }
}
