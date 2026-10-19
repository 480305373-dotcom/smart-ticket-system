package com.smartticket.backend.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component("ticketRequestContextFilter")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestContextFilter extends OncePerRequestFilter {
  @org.springframework.beans.factory.annotation.Value("${HOSTNAME:local}")
  private String instance;
  @Override
  protected void doFilterInternal(HttpServletRequest r, HttpServletResponse s, FilterChain chain)
      throws ServletException, IOException {
    String id = UUID.randomUUID().toString();
    s.setHeader("X-Request-Id", id);
    s.setHeader("X-SmartTicket-Service", "core");
    s.setHeader("X-SmartTicket-Instance", instance);
    MDC.put("requestId", id);
    try {
      chain.doFilter(r, s);
    } finally {
      MDC.remove("requestId");
    }
  }
}
