package com.smartticket.backend.config;

import org.springframework.context.annotation.*;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.web.client.RestClient;

@Configuration
public class ServiceClientConfig {
  @Bean
  @LoadBalanced
  @Scope("prototype")
  RestClient.Builder serviceRestClient() {
    var factory = new org.springframework.http.client.JdkClientHttpRequestFactory(
        java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(3)).build());
    factory.setReadTimeout(java.time.Duration.ofSeconds(8));
    return RestClient.builder().requestFactory(factory);
  }

  @Bean
  @Primary
  @Scope("prototype")
  RestClient.Builder plainRestClient() {
    return RestClient.builder();
  }
}
