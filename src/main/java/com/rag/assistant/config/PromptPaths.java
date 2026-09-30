package com.rag.assistant.config;

/** Classpath locations of the prompt templates in {@code src/main/resources/prompts}. */
public final class PromptPaths {

  public static final String SYSTEM = "classpath:prompts/system.st";
  public static final String REWRITE = "classpath:prompts/rewrite.st";
  public static final String RAG = "classpath:prompts/rag.st";
  public static final String NO_CONTEXT = "classpath:prompts/no-context.st";

  private PromptPaths() {}
}
