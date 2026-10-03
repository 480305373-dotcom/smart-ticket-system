package com.smartticket.backend.security;

import com.smartticket.backend.service.JwtService;
import com.smartticket.backend.service.TokenBlacklistService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtService jwtService;
  private final TokenBlacklistService tokenBlacklistService;

  public JwtAuthenticationFilter(
      JwtService jwtService, TokenBlacklistService tokenBlacklistService) {
    this.jwtService = jwtService;
    this.tokenBlacklistService = tokenBlacklistService;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String authHeader = request.getHeader("Authorization");

    // 没有 Token，继续交给 Spring Security
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {

      filterChain.doFilter(request, response);

      return;
    }

    String token = authHeader.substring(7);

    try {

      // Reject invalid or expired JWTs before consulting the revocation store.
      Claims claims = jwtService.parseToken(token);
      if (tokenBlacklistService.isBlacklisted(token)) {
        error(response, 401, "Token已失效，请重新登录");
        return;
      }

      String username = claims.getSubject();

      String role = claims.get("role", String.class);

      Number userIdNumber = claims.get("userId", Number.class);

      if (userIdNumber == null
          || userIdNumber.longValue() <= 0
          || username == null
          || username.isBlank()
          || !("USER".equals(role) || "ADMIN".equals(role))) {
        throw new IllegalArgumentException("JWT claims invalid");
      }

      Long userId = userIdNumber.longValue();

      AuthUser authUser = new AuthUser(userId, username, role);

      var authentication =
          UsernamePasswordAuthenticationToken.authenticated(
              authUser, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));

      SecurityContextHolder.getContext().setAuthentication(authentication);

    } catch (JwtException | IllegalArgumentException e) {
      error(response, 401, "Token无效或已过期");
      return;
    } catch (DataAccessException e) {
      logger.error("JWT revocation store unavailable", e);
      error(response, 503, "认证服务暂时不可用，请稍后重试");
      return;
    }
    // Downstream business exceptions belong to the normal API exception handler.
    filterChain.doFilter(request, response);
  }

  private void error(HttpServletResponse response, int status, String message) throws IOException {
    SecurityContextHolder.clearContext();
    response.setStatus(status);
    response.setContentType("application/json;charset=UTF-8");
    response
        .getWriter()
        .write("{\"code\":" + status + ",\"message\":\"" + message + "\",\"data\":null}");
  }
}
