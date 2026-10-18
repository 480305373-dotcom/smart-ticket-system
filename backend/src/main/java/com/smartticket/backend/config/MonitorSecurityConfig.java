package com.smartticket.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@ConditionalOnProperty(name = "app.monitor.enabled", havingValue = "true")
public class MonitorSecurityConfig {
  @Bean
  @Order(1)
  SecurityFilterChain metricsSecurity(
      HttpSecurity http, @Value("${MONITOR_PASSWORD}") String password) throws Exception {
    if (password.length() < 20)
      throw new IllegalArgumentException("MONITOR_PASSWORD must contain at least 20 characters");
    var encoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
    var users =
        new org.springframework.security.provisioning.InMemoryUserDetailsManager(
            org.springframework.security.core.userdetails.User.withUsername("monitor")
                .password(encoder.encode(password))
                .roles("MONITOR")
                .build());
    var provider =
        new org.springframework.security.authentication.dao.DaoAuthenticationProvider(users);
    provider.setPasswordEncoder(encoder);
    http.securityMatcher("/actuator/prometheus")
        .authenticationManager(
            new org.springframework.security.authentication.ProviderManager(provider))
        .csrf(c -> c.disable())
        .sessionManagement(
            s ->
                s.sessionCreationPolicy(
                    org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
        .httpBasic(org.springframework.security.config.Customizer.withDefaults())
        .authorizeHttpRequests(a -> a.anyRequest().hasRole("MONITOR"));
    return http.build();
  }
}
