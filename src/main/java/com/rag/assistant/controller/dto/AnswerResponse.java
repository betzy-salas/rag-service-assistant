package com.rag.assistant.controller.dto;

import java.util.List;

public record AnswerResponse(String answer, List<SourceResponse> sources) {}
