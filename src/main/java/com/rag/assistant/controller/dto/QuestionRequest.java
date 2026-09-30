package com.rag.assistant.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** Question to answer. {@code spaceKey} restricts the search to one Confluence space. */
public record QuestionRequest(
    @NotBlank @Size(max = 2000) String question, @Nullable @Size(max = 255) String spaceKey) {}
