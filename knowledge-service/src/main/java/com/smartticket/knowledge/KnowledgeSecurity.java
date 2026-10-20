package com.smartticket.knowledge;

import com.smartticket.backend.security.AuthUser;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.client.*;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class KnowledgeSecurity {
  @Bean SecurityFilterChain knowledgeFilterChain(HttpSecurity http,
      @org.springframework.beans.factory.annotation.Qualifier("serviceRestClient") RestClient.Builder builder,
      @Value("${app.internal-key}") String key) throws Exception {
    if (key.length()<32) throw new IllegalArgumentException("Internal service key is too short");
    var core=builder.baseUrl("http://smartticket-core").defaultHeader("X-Service-Key",key).build();
    var filter=new OncePerRequestFilter() {
      @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
          throws ServletException,IOException {
        response.setHeader("X-SmartTicket-Service","knowledge");
        response.setHeader("X-SmartTicket-Instance",System.getenv().getOrDefault("HOSTNAME","local"));
        if (request.getRequestURI().startsWith("/actuator/health")) { chain.doFilter(request,response);return; }
        try {
          if(request.getRequestURI().startsWith("/internal/")) {
            String supplied=Objects.toString(request.getHeader("X-Service-Key"),"");
            if(!MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8))) {
              error(response,403);return;
            }
            SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "core-service",null,List.of(new SimpleGrantedAuthority("ROLE_SERVICE"))));
          } else {
            String token=request.getHeader("Authorization");
            if(token==null || !token.startsWith("Bearer ")) {error(response,401);return;}
            // Introspection preserves immediate revocation, account disabling and role changes.
            var result=core.get().uri("/internal/identity").header("Authorization",token).retrieve().body(Map.class);
            var data=(Map<?,?>)Objects.requireNonNull(result).get("data");
            var user=new AuthUser(((Number)data.get("userId")).longValue(),data.get("username").toString(),data.get("role").toString());
            SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                user,null,List.of(new SimpleGrantedAuthority("ROLE_"+user.role()))));
          }
          chain.doFilter(request,response);
        } catch(RestClientResponseException e) {
            int status=e.getStatusCode().value();
            if(status==429)response.setHeader("Retry-After","60");
            error(response,status==401||status==429?status:503);
          }
          catch(RestClientException e) {error(response,503);}
        finally { SecurityContextHolder.clearContext(); }
      }
    };
    return http.csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(
        org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(a->a.requestMatchers("/actuator/health/**","/actuator/health").permitAll()
          .requestMatchers("/internal/**").hasRole("SERVICE").anyRequest().authenticated())
        .addFilterBefore(filter,UsernamePasswordAuthenticationFilter.class).build();
  }
  static void error(HttpServletResponse response,int code)throws IOException {
    response.setStatus(code);response.setContentType("application/json;charset=UTF-8");
    String message=code==429?"请求过于频繁，请稍后重试":"认证失败或认证服务不可用";
    response.getWriter().write("{\"code\":"+code+",\"message\":\""+message+"\",\"data\":null}");
  }
}
