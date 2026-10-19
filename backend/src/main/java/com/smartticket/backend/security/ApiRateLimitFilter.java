package com.smartticket.backend.security;

import com.smartticket.backend.service.RateLimitService;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class ApiRateLimitFilter extends OncePerRequestFilter {
  private final RateLimitService limits;
  private final int perMinute;

  public ApiRateLimitFilter(RateLimitService limits, int perMinute) {
    this.limits = limits;
    this.perMinute = perMinute;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest r) {
    // Knowledge requests pass this limiter once through identity introspection.
    return "OPTIONS".equals(r.getMethod()) || r.getRequestURI().startsWith("/actuator/");
  }

  @Override
  protected void doFilterInternal(HttpServletRequest r, HttpServletResponse s, FilterChain chain)
      throws ServletException, IOException {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    boolean login =
        r.getRequestURI().equals("/users/login") || r.getRequestURI().equals("/users/register");
    String identity =
        auth != null && auth.getPrincipal() instanceof AuthUser u
            ? "user:" + u.userId()
            : "ip:" + r.getRemoteAddr();
    try {
      boolean ai = r.getRequestURI().startsWith("/ai/");
      if (!limits.allow(
          (login ? "login:" : ai ? "ai:" : "api:") + identity, login ? 30 : ai ? 10 : perMinute)) {
        s.setHeader("Retry-After", "60");
        error(s, 429, "请求过于频繁，请稍后重试");
        return;
      }
    } catch (org.springframework.dao.DataAccessException e) {
      error(s, 503, "限流服务暂时不可用，请稍后重试");
      return;
    }
    chain.doFilter(r, s);
  }

  private void error(HttpServletResponse s, int status, String message) throws IOException {
    s.setStatus(status);
    s.setContentType("application/json;charset=UTF-8");
    s.getWriter().write("{\"code\":" + status + ",\"message\":\"" + message + "\",\"data\":null}");
  }
}
