package com.rag.assistant.config;

import com.rag.assistant.controller.ApiPaths;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Allows the browser front-end, served from another origin, to call the API. Actuator endpoints
 * have their own handler mapping, so their CORS is set with {@code management.endpoints.web.cors.*}
 * in {@code application.properties}.
 */
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
@RequiredArgsConstructor
public class CorsConfig implements WebMvcConfigurer {

  private final CorsProperties properties;

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping(ApiPaths.QUESTIONS)
        .allowedOrigins(properties.allowedOrigins().toArray(String[]::new))
        .allowedMethods(HttpMethod.POST.name());
  }
}
