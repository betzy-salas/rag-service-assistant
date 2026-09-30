package com.rag.assistant.controller.advice;

import com.openai.errors.OpenAIException;
import com.rag.assistant.exception.ErrorMessages;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Translates exceptions into RFC 9457 {@link ProblemDetail} responses.
 *
 * <p>Spring MVC errors, such as a blank {@code question} or an unreadable body, are handled by {@link
 * ResponseEntityExceptionHandler} with the same format.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  @ExceptionHandler(OpenAIException.class)
  ResponseEntity<ProblemDetail> handleOpenAi(OpenAIException exception) {
    log.warn("Error calling OpenAI: {}", exception.getMessage());
    return problem(HttpStatus.BAD_GATEWAY, ErrorMessages.ANSWER_FAILED);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> handleUnexpected(Exception exception) {
    log.error("Unexpected error", exception);
    return problem(HttpStatus.INTERNAL_SERVER_ERROR, ErrorMessages.INTERNAL_ERROR);
  }

  private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
    return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detail));
  }
}
