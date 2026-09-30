package com.rag.assistant.exception;

/** Error messages returned to API clients in the {@code detail} field of the problem details. */
public final class ErrorMessages {

  public static final String ANSWER_FAILED = "Could not generate the answer with OpenAI";
  public static final String INTERNAL_ERROR = "Internal service error";

  private ErrorMessages() {}
}
