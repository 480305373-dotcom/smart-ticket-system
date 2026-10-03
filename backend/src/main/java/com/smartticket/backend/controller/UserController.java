package com.smartticket.backend.controller;

import com.smartticket.backend.dto.ApiResponse;
import com.smartticket.backend.dto.LoginRequest;
import com.smartticket.backend.dto.LoginResponse;
import com.smartticket.backend.dto.RegisterRequest;
import com.smartticket.backend.entity.User;
import com.smartticket.backend.service.JwtService;
import com.smartticket.backend.service.TokenBlacklistService;
import com.smartticket.backend.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
public class UserController {

  private final UserService userService;
  private final com.smartticket.backend.service.UserDirectoryService directory;
  private final JwtService jwtService;
  private final TokenBlacklistService tokenBlacklistService;

  public UserController(
      UserService userService,
      JwtService jwtService,
      TokenBlacklistService tokenBlacklistService,
      com.smartticket.backend.service.UserDirectoryService directory) {
    this.userService = userService;
    this.directory = directory;
    this.jwtService = jwtService;
    this.tokenBlacklistService = tokenBlacklistService;
  }

  // 查询用户
  @GetMapping("/users/{id}")
  public ApiResponse<User> getUserById(
      @PathVariable Long id, org.springframework.security.core.Authentication authentication) {
    var current = (com.smartticket.backend.security.AuthUser) authentication.getPrincipal();
    if (!"ADMIN".equals(current.role()) && !id.equals(current.userId()))
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.FORBIDDEN, "无权查看其他用户");
    return ApiResponse.success(userService.getUserById(id));
  }

  // 注册
  @PostMapping("/users/register")
  public ApiResponse<User> register(@Valid @RequestBody RegisterRequest request) {
    return ApiResponse.success(userService.register(request));
  }

  // 登录
  @PostMapping("/users/login")
  public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {

    User user = userService.login(request);

    String token = jwtService.generateToken(user);

    LoginResponse response =
        new LoginResponse(token, user.getId(), user.getUsername(), user.getRole());

    return ApiResponse.success(response);
  }

  // 退出登录
  @PostMapping("/users/logout")
  public ApiResponse<Void> logout(@RequestHeader("Authorization") String authorization) {

    if (authorization == null || !authorization.startsWith("Bearer ")) {

      return ApiResponse.error(401, "Token不存在");
    }

    String token = authorization.substring(7);

    // Token还剩多久有效
    long ttlMillis = jwtService.getRemainingExpirationMillis(token);

    // 将Token加入Redis黑名单
    if (ttlMillis > 0) {
      tokenBlacklistService.blacklist(token, ttlMillis);
    }

    return ApiResponse.success(null);
  }

  @GetMapping("/users/handlers")
  public ApiResponse<java.util.List<com.smartticket.backend.dto.UserOption>> handlers(
      org.springframework.security.core.Authentication authentication) {
    var user = (com.smartticket.backend.security.AuthUser) authentication.getPrincipal();
    if (!"ADMIN".equals(user.role()))
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.FORBIDDEN, "只有管理员可以查询处理人");
    return ApiResponse.success(directory.handlers());
  }
}
