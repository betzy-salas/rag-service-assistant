package com.rag.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rag.assistant.ChatResponses;
import com.rag.assistant.TestInfrastructure;
import com.rag.assistant.service.model.Answer;
import com.rag.assistant.service.model.ChunkMetadata;
import com.rag.assistant.service.model.Source;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** Runs the pipeline against a real pgvector table with the same layout the ingestion service creates. */
@SpringBootTest
@Import(TestInfrastructure.class)
class AnswerIntegrationTest {

  @Autowired AnswerService answerService;
  @Autowired VectorStore vectorStore;
  @Autowired ChatModel chatModel;
  @Autowired JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    // The mocked chat model is shared by every test of the Spring context.
    clearInvocations(chatModel);
    jdbcTemplate.execute("DELETE FROM confluence_chunk");
    vectorStore.add(
        List.of(
            chunk("1", "DS", "Guía de despliegue", "El despliegue se hace con Jenkins"),
            chunk("1", "DS", "Guía de despliegue", "Los pipelines viven en el repositorio infra"),
            chunk("2", "DS", "Onboarding", "El primer día se piden los accesos"),
            chunk("9", "HR", "Vacaciones", "Las vacaciones se piden en el portal")));
    when(chatModel.call(any(Prompt.class)))
        .thenAnswer(
            invocation ->
                ChatResponses.of(
                    ChatResponses.isRewrite(invocation.getArgument(0))
                        ? "¿Cómo se despliega el servicio?"
                        : "respuesta"));
  }

  @Test
  void answersCitingThePagesOfTheRetrievedChunks() {
    Answer answer = answerService.answer("¿Cómo se despliega?", null);

    assertThat(answer.text()).isEqualTo("respuesta");
    assertThat(answer.sources())
        .containsExactlyInAnyOrder(
            new Source("Guía de despliegue", "https://wiki/pages/1"),
            new Source("Onboarding", "https://wiki/pages/2"),
            new Source("Vacaciones", "https://wiki/pages/9"));
    ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
    verify(chatModel, times(2)).call(prompt.capture());
    assertThat(prompt.getAllValues().getFirst().getUserMessage().getText()).contains("¿Cómo se despliega?");
    assertThat(prompt.getAllValues().getLast().getUserMessage().getText())
        .contains("El despliegue se hace con Jenkins")
        .contains("¿Cómo se despliega?");
  }

  @Test
  void spaceKeyRestrictsTheSourcesToThatSpace() {
    Answer answer = answerService.answer("¿Cómo se despliega?", "DS");

    assertThat(answer.sources())
        .containsExactlyInAnyOrder(
            new Source("Guía de despliegue", "https://wiki/pages/1"),
            new Source("Onboarding", "https://wiki/pages/2"));
  }

  @Test
  void unknownSpaceKeyGivesNoSources() {
    Answer answer = answerService.answer("¿Cómo se despliega?", "XX");

    assertThat(answer.sources()).isEmpty();
  }

  private static Document chunk(String pageId, String spaceKey, String title, String text) {
    return new Document(
        text,
        Map.of(
            ChunkMetadata.PAGE_ID, pageId,
            ChunkMetadata.SPACE_KEY, spaceKey,
            ChunkMetadata.TITLE, title,
            ChunkMetadata.URL, "https://wiki/pages/" + pageId));
  }
}
