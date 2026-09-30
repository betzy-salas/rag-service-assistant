package com.rag.assistant.controller.mapper;

import com.rag.assistant.controller.dto.AnswerResponse;
import com.rag.assistant.service.model.Answer;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface AnswerResponseMapper {

  @Mapping(target = "answer", source = "text")
  AnswerResponse toResponse(Answer answer);
}
