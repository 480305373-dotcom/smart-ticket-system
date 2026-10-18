package com.smartticket.backend.config;

import com.smartticket.backend.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthenticationFilter;

  public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
    this.jwtAuthenticationFilter = jwtAuthenticationFilter;
  }

  @Bean
  public org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource(
      @org.springframework.beans.factory.annotation.Value("${app.cors.origins}") String origins) {
    var config = new org.springframework.web.cors.CorsConfiguration();
    config.setAllowedOrigins(java.util.Arrays.asList(origins.split(",")));
    config.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(java.util.List.of("Authorization", "Content-Type", "X-Request-Id"));
    config.setExposedHeaders(java.util.List.of("X-Request-Id", "Retry-After"));
    var source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      com.smartticket.backend.service.RateLimitService limits,
      @org.springframework.beans.factory.annotation.Value("${app.rate-limit.per-minute:120}")
          int perMinute)
      throws Exception {

    http.csrf(csrf -> csrf.disable())
        .cors(org.springframework.security.config.Customizer.withDefaults())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            exception ->
                exception
                    .authenticationEntryPoint(
                        (request, response, authException) -> {
                          response.setStatus(401);
                          response.setContentType("application/json;charset=UTF-8");

                          response
                              .getWriter()
                              .write(
                                  """
                                  {
                                    "code":401,
                                    "message":"请先登录",
                                    "data":null
                                  }
                                  """);
                        })
                    .accessDeniedHandler(
                        (request, response, accessDeniedException) -> {
                          var authentication =
                              org.springframework.security.core.context.SecurityContextHolder
                                  .getContext()
                                  .getAuthentication();

                          // 未登录 / 匿名用户
                          if (authentication == null
                              || authentication
                                  instanceof
                                  org.springframework.security.authentication
                                      .AnonymousAuthenticationToken) {

                            response.setStatus(401);
                            response.setContentType("application/json;charset=UTF-8");

                            response
                                .getWriter()
                                .write(
                                    """
                                    {
                                      "code":401,
                                      "message":"请先登录",
                                      "data":null
                                    }
                                    """);

                            return;
                          }

                          // 已登录，但是权限不足
                          response.setStatus(403);
                          response.setContentType("application/json;charset=UTF-8");

                          response
                              .getWriter()
                              .write(
                                  """
                                  {
                                    "code":403,
                                    "message":"没有权限访问",
                                    "data":null
                                  }
                                  """);
                        }))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/users/register",
                        "/users/login",
                        "/actuator/health",
                        "/actuator/health/liveness",
                        "/actuator/health/readiness",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**")
                    .permitAll()
                    .requestMatchers("/admin/**", "/actuator/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(
            new com.smartticket.backend.security.ApiRateLimitFilter(limits, perMinute),
            JwtAuthenticationFilter.class);

    return http.build();
  }

  @Bean
  public org.springframework.boot.web.servlet.FilterRegistrationBean<JwtAuthenticationFilter>
      jwtRegistration() {
    var registration =
        new org.springframework.boot.web.servlet.FilterRegistrationBean<>(jwtAuthenticationFilter);
    registration.setEnabled(false);
    return registration;
  }
}
