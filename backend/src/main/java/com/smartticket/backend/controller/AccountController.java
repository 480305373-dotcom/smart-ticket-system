package com.smartticket.backend.controller;

import com.smartticket.backend.dto.ApiResponse;
import com.smartticket.backend.security.AuthUser;
import com.smartticket.backend.service.AccountSecurityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public class AccountController {
  private final AccountSecurityService accounts;
  private final com.smartticket.backend.service.UserService users;

  public AccountController(
      AccountSecurityService accounts, com.smartticket.backend.service.UserService users) {
    this.accounts = accounts;
    this.users = users;
  }

  @PostMapping("/admin/users")
  public ApiResponse<?> create(
      @Valid @RequestBody com.smartticket.backend.dto.RegisterRequest request) {
    return ApiResponse.success(users.register(request));
  }

  public record PasswordRequest(
      @NotBlank String oldPassword, @NotBlank @Size(min = 8, max = 72) String newPassword) {}

  public record AccountRequest(
      @NotBlank @Pattern(regexp = "USER|ADMIN") String role, @NotNull Boolean enabled) {}

  @GetMapping("/admin/users")
  public ApiResponse<?> page(
      @RequestParam(defaultValue = "") String keyword,
      @RequestParam(defaultValue = "1") long current,
      @RequestParam(defaultValue = "10") long size) {
    return ApiResponse.success(
        Map.of("records", accounts.page(keyword, current, size), "total", accounts.count(keyword)));
  }

  @PatchMapping("/admin/users/{id}")
  public ApiResponse<?> update(
      @PathVariable Long id, @Valid @RequestBody AccountRequest request, Authentication auth) {
    accounts.update(
        id, request.role(), request.enabled(), ((AuthUser) auth.getPrincipal()).userId());
    return ApiResponse.success(null);
  }

  @PostMapping("/users/change-password")
  public ApiResponse<?> password(@Valid @RequestBody PasswordRequest request, Authentication auth) {
    accounts.password(
        ((AuthUser) auth.getPrincipal()).userId(), request.oldPassword(), request.newPassword());
    return ApiResponse.success(null);
  }
}
