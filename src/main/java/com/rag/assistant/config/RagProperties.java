package com.rag.assistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Retrieval settings under {@code app.rag.*}, provided by Consul: how many chunks to retrieve and
 * the minimum similarity they need to be used as context.
 */
@ConfigurationProperties(prefix = RagProperties.PREFIX)
public record RagProperties(
    @DefaultValue("5") int topK, @DefaultValue("0.5") double similarityThreshold) {

  public static final String PREFIX = "app.rag";
}
