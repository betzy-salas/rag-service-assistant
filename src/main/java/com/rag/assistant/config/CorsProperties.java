package com.rag.assistant.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Origins allowed to call the API from a browser, under {@code app.cors.*}. Defaults to the Vite
 * development server of the front-end project.
 */
@ConfigurationProperties(prefix = CorsProperties.PREFIX)
public record CorsProperties(@DefaultValue("http://localhost:5173") List<String> allowedOrigins) {

  public static final String PREFIX = "app.cors";
}
