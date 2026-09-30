package com.rag.assistant.controller;

import com.rag.assistant.controller.dto.AnswerResponse;
import com.rag.assistant.controller.dto.QuestionRequest;
import com.rag.assistant.controller.mapper.AnswerResponseMapper;
import com.rag.assistant.service.AnswerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint to ask questions about the indexed Confluence pages. Each question is answered on its own. */
@RestController
@RequiredArgsConstructor
public class QuestionController {

  private final AnswerService answerService;
  private final AnswerResponseMapper responseMapper;

  @PostMapping(ApiPaths.QUESTIONS)
  ResponseEntity<AnswerResponse> ask(@Valid @RequestBody QuestionRequest request) {
    return ResponseEntity.ok(
        responseMapper.toResponse(answerService.answer(request.question(), request.spaceKey())));
  }
}
