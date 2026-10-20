package com.smartticket.backend.controller;

import com.smartticket.backend.dto.ApiResponse;
import com.smartticket.backend.security.AuthUser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@ConditionalOnProperty(name = "app.knowledge.remote", havingValue = "true")
public class IdentityController {
  private final String key;
  public IdentityController(@Value("${app.internal-key}") String key) {
    if (key.length() < 32) throw new IllegalArgumentException("Internal service key is too short");
    this.key = key;
  }
  @GetMapping("/internal/identity")
  public ApiResponse<AuthUser> identity(
      @RequestHeader(value="X-Service-Key", defaultValue="") String supplied,
      Authentication authentication) {
    if (!MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8)))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "内部服务认证失败");
    return ApiResponse.success((AuthUser) authentication.getPrincipal());
  }
}
