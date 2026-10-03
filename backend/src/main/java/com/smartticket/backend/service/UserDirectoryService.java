package com.smartticket.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartticket.backend.dto.UserOption;
import com.smartticket.backend.entity.User;
import com.smartticket.backend.mapper.UserMapper;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class UserDirectoryService {
  private final UserMapper users;

  public UserDirectoryService(UserMapper users) {
    this.users = users;
  }

  public Map<Long, String> names(Collection<Long> ids) {
    var unique = ids.stream().filter(Objects::nonNull).distinct().toList();
    if (unique.isEmpty()) return Map.of();
    return users
        .selectList(
            new LambdaQueryWrapper<User>()
                .select(User::getId, User::getUsername)
                .in(User::getId, unique))
        .stream()
        .collect(Collectors.toMap(User::getId, User::getUsername));
  }

  public List<UserOption> handlers() {
    return users
        .selectList(
            new LambdaQueryWrapper<User>()
                .select(User::getId, User::getUsername)
                .eq(User::getRole, "ADMIN")
                .orderByAsc(User::getUsername))
        .stream()
        .map(u -> new UserOption(u.getId(), u.getUsername()))
        .toList();
  }
}
