package com.rag.assistant.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openai.errors.OpenAIIoException;
import com.rag.assistant.config.CorsConfig;
import com.rag.assistant.controller.mapper.AnswerResponseMapperImpl;
import com.rag.assistant.service.AnswerService;
import com.rag.assistant.service.model.Answer;
import com.rag.assistant.service.model.Source;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(QuestionController.class)
@Import({AnswerResponseMapperImpl.class, CorsConfig.class})
class QuestionControllerTest {

  @Autowired MockMvc mockMvc;

  @MockitoBean AnswerService answerService;

  @Test
  void askReturns200WithAnswerAndSources() throws Exception {
    when(answerService.answer("¿Cómo se despliega?", "DS"))
        .thenReturn(
            new Answer("Con Jenkins.", List.of(new Source("Guía de despliegue", "https://wiki/pages/1"))));

    mockMvc
        .perform(
            post("/questions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"¿Cómo se despliega?\",\"spaceKey\":\"DS\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.answer").value("Con Jenkins."))
        .andExpect(jsonPath("$.sources[0].title").value("Guía de despliegue"))
        .andExpect(jsonPath("$.sources[0].url").value("https://wiki/pages/1"));
  }

  @Test
  void spaceKeyIsOptional() throws Exception {
    when(answerService.answer("pregunta", null)).thenReturn(new Answer("respuesta", List.of()));

    mockMvc
        .perform(post("/questions").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"pregunta\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.answer").value("respuesta"))
        .andExpect(jsonPath("$.sources").isEmpty());
  }

  @Test
  void blankQuestionReturns400() throws Exception {
    mockMvc
        .perform(post("/questions").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"  \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.instance").value("/questions"));
  }

  @Test
  void unreadableBodyReturns400() throws Exception {
    mockMvc
        .perform(post("/questions").contentType(MediaType.APPLICATION_JSON).content("not json"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
  }

  @Test
  void openAiFailureReturns502() throws Exception {
    when(answerService.answer(any(), any())).thenThrow(new OpenAIIoException("connection reset"));

    mockMvc
        .perform(post("/questions").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"pregunta\"}"))
        .andExpect(status().isBadGateway())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.detail").value("Could not generate the answer with OpenAI"));
  }

  @Test
  void unexpectedErrorReturns500WithoutInternalDetails() throws Exception {
    when(answerService.answer(any(), any())).thenThrow(new IllegalStateException("detalle interno"));

    mockMvc
        .perform(post("/questions").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"pregunta\"}"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.detail").value("Internal service error"));
  }

  @Test
  void preflightFromFrontEndOriginIsAllowed() throws Exception {
    mockMvc
        .perform(
            options("/questions")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "POST"));
  }

  @Test
  void preflightFromUnknownOriginIsRejected() throws Exception {
    mockMvc
        .perform(
            options("/questions")
                .header(HttpHeaders.ORIGIN, "http://evil.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
        .andExpect(status().isForbidden())
        .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
  }
}
