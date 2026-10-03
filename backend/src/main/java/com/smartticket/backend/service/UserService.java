package com.smartticket.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartticket.backend.dto.LoginRequest;
import com.smartticket.backend.dto.RegisterRequest;
import com.smartticket.backend.entity.User;
import com.smartticket.backend.exception.LoginException;
import com.smartticket.backend.mapper.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserService {
  private final UserMapper userMapper;
  private final PasswordEncoder passwordEncoder;
  private final LoginAttemptService loginAttemptService;

  public UserService(
      UserMapper userMapper,
      PasswordEncoder passwordEncoder,
      LoginAttemptService loginAttemptService) {
    this.userMapper = userMapper;
    this.passwordEncoder = passwordEncoder;
    this.loginAttemptService = loginAttemptService;
  }

  public User getUserById(Long id) {

    User user = userMapper.selectById(id);
    if (user == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
    return user;
  }

  @org.springframework.cache.annotation.CacheEvict(value = "ticketStatistics", allEntries = true)
  public User register(RegisterRequest request) {
    if (getUserByUsername(request.getUsername()) != null) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");
    }
    if (request.getPassword().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码的UTF-8长度不能超过72字节");
    }
    String passwordHash = passwordEncoder.encode(request.getPassword());
    User user = new User();
    user.setUsername(request.getUsername());
    user.setPasswordHash(passwordHash);
    user.setRole("USER");

    userMapper.insert(user);
    return user;
  }

  public User getUserByUsername(String username) {
    return userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
  }

  public User login(LoginRequest request) {

    String username = request.getUsername();

    if (loginAttemptService.isBlocked(username)) {
      throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "登录失败次数过多，请10分钟后再试");
    }

    User user = getUserByUsername(username);

    if (user == null) {

      handleLoginFailure(username);

      throw new LoginException("用户名或密码错误");
    }

    boolean matched = passwordEncoder.matches(request.getPassword(), user.getPasswordHash());

    if (!matched) {

      handleLoginFailure(username);

      throw new LoginException("用户名或密码错误");
    }

    // 登录成功，清除之前累计的失败次数
    loginAttemptService.clearFailures(username);

    return user;
  }

  private void handleLoginFailure(String username) {

    long failures = loginAttemptService.recordFailure(username);

    if (failures >= 5) {
      throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "登录失败次数过多，请10分钟后再试");
    }
  }
}
