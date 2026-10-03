package com.smartticket.backend;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.smartticket.backend.entity.User;
import com.smartticket.backend.security.JwtAuthenticationFilter;
import com.smartticket.backend.service.JwtService;
import com.smartticket.backend.service.TokenBlacklistService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

class JwtFilterTests {
  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  private JwtService jwt(long expiration) {
    var service = new JwtService();
    ReflectionTestUtils.setField(
        service, "secret", "c21hcnQtdGlja2V0LWRldi1vbmx5LWtleS0zMi1ieXRlcy0xMjM0NQ==");
    ReflectionTestUtils.setField(service, "expiration", expiration);
    return service;
  }

  private String token(JwtService service) {
    var u = new User();
    u.setId(1L);
    u.setUsername("jwt_test");
    u.setRole("USER");
    return service.generateToken(u);
  }

  private MockHttpServletRequest request(String token) {
    var r = new MockHttpServletRequest();
    r.addHeader("Authorization", "Bearer " + token);
    return r;
  }

  @Test
  void expiredTokenIsRejectedWithoutDatabaseRead() throws Exception {
    var jwt = jwt(-60000);
    var store = mock(TokenBlacklistService.class);
    var chain = mock(FilterChain.class);
    var response = new MockHttpServletResponse();
    new JwtAuthenticationFilter(jwt, store).doFilter(request(token(jwt)), response, chain);
    assertEquals(401, response.getStatus());
    verifyNoInteractions(store, chain);
    assertNull(SecurityContextHolder.getContext().getAuthentication());
  }

  @Test
  void unavailableRevocationDatabaseReturnsSafe503() throws Exception {
    var jwt = jwt(60000);
    var store = mock(TokenBlacklistService.class);
    var chain = mock(FilterChain.class);
    when(store.isBlacklisted(anyString()))
        .thenThrow(new DataAccessResourceFailureException("secret database connection details"));
    var response = new MockHttpServletResponse();
    new JwtAuthenticationFilter(jwt, store).doFilter(request(token(jwt)), response, chain);
    assertEquals(503, response.getStatus());
    assertFalse(response.getContentAsString().contains("secret"));
    verifyNoInteractions(chain);
    assertNull(SecurityContextHolder.getContext().getAuthentication());
  }

  @Test
  void revokedTokenCannotReachController() throws Exception {
    var jwt = jwt(60000);
    var store = mock(TokenBlacklistService.class);
    var chain = mock(FilterChain.class);
    when(store.isBlacklisted(anyString())).thenReturn(true);
    var response = new MockHttpServletResponse();
    new JwtAuthenticationFilter(jwt, store).doFilter(request(token(jwt)), response, chain);
    assertEquals(401, response.getStatus());
    verifyNoInteractions(chain);
  }

  @Test
  void downstreamBusinessErrorIsNotConvertedToJwt401() throws Exception {
    var jwt = jwt(60000);
    var store = mock(TokenBlacklistService.class);
    var chain = mock(FilterChain.class);
    var request = request(token(jwt));
    var response = new MockHttpServletResponse();
    doThrow(new IllegalArgumentException("business validation"))
        .when(chain)
        .doFilter(request, response);
    assertThrows(
        IllegalArgumentException.class,
        () -> new JwtAuthenticationFilter(jwt, store).doFilter(request, response, chain));
    assertEquals(200, response.getStatus());
  }
}
