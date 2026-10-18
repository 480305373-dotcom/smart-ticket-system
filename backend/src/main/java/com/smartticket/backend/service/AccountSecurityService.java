package com.smartticket.backend.service;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountSecurityService {
  private final JdbcTemplate jdbc;
  private final PasswordEncoder encoder;

  public AccountSecurityService(JdbcTemplate jdbc, PasswordEncoder encoder) {
    this.jdbc = jdbc;
    this.encoder = encoder;
  }

  public boolean valid(Long id, String role, int version) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM app_user WHERE id=? AND enabled=TRUE AND role=? AND"
                + " auth_version=?)",
            Boolean.class,
            id,
            role,
            version));
  }

  public List<Map<String, Object>> page(String keyword, long current, long size) {
    TicketService.validatePage(current, size);
    return jdbc.queryForList(
        "SELECT id,username,role,enabled FROM app_user WHERE username ILIKE ? ORDER BY id LIMIT ?"
            + " OFFSET ?",
        "%" + keyword + "%",
        size,
        (current - 1) * size);
  }

  public long count(String keyword) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM app_user WHERE username ILIKE ?", Long.class, "%" + keyword + "%");
  }

  @Transactional
  public void update(Long id, String role, boolean enabled, Long operator) {
    if (id.equals(operator))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能修改自己的角色或停用自己的账号");
    jdbc.execute("SELECT pg_advisory_xact_lock(741852964)");
    if (!Set.of("USER", "ADMIN").contains(role))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "角色无效");
    if (jdbc.update(
            "UPDATE app_user SET role=?,enabled=?,auth_version=auth_version+1 WHERE id=?",
            role,
            enabled,
            id)
        != 1) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
    if (jdbc.queryForObject(
            "SELECT count(*) FROM app_user WHERE role='ADMIN' AND enabled=TRUE", Long.class)
        == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "必须保留至少一个启用的管理员");
    if (!enabled || !"ADMIN".equals(role)) {
      var released =
          jdbc.queryForList(
              "UPDATE ticket SET assignee_id=NULL,updated_at=CURRENT_TIMESTAMP WHERE assignee_id=?"
                  + " AND status IN ('OPEN','IN_PROGRESS') RETURNING id",
              Long.class,
              id);
      for (Long ticketId : released)
        jdbc.update(
            "INSERT INTO"
                + " ticket_history(ticket_id,operator_id,action,old_assignee_id,new_assignee_id)"
                + " VALUES (?,?,'ASSIGN',?,NULL)",
            ticketId,
            operator,
            id);
    }
    jdbc.update(
        "INSERT INTO operation_log(user_id,action,target_type,target_id,detail) VALUES"
            + " (?,'UPDATE_ACCOUNT','USER',?,?)",
        operator,
        id,
        "角色：" + role + "，启用：" + enabled);
  }

  @Transactional
  public void password(Long id, String oldPassword, String newPassword) {
    if (newPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码UTF-8长度不能超过72字节");
    var rows = jdbc.queryForList("SELECT password_hash FROM app_user WHERE id=? FOR UPDATE", id);
    if (rows.isEmpty()
        || !encoder.matches(oldPassword, (String) rows.getFirst().get("password_hash")))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "原密码不正确");
    if (encoder.matches(newPassword, (String) rows.getFirst().get("password_hash")))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新密码不能与原密码相同");
    jdbc.update(
        "UPDATE app_user SET password_hash=?,auth_version=auth_version+1 WHERE id=?",
        encoder.encode(newPassword),
        id);
    jdbc.update(
        "INSERT INTO operation_log(user_id,action,target_type,target_id,detail) VALUES"
            + " (?,'CHANGE_PASSWORD','USER',?,'修改密码并撤销全部旧会话')",
        id,
        id);
  }
}
