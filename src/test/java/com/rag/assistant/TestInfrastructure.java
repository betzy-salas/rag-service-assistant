package com.rag.assistant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared test infrastructure: a real PostgreSQL with pgvector, fake embeddings and a mocked chat
 * model, so tests never call OpenAI.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestInfrastructure {

  @Bean
  @ServiceConnection
  PostgreSQLContainer postgres() {
    return new PostgreSQLContainer(
        DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));
  }

  @Bean
  ChatModel chatModel() {
    ChatModel chatModel = mock(ChatModel.class);
    // The ChatClient copies the model options into every request, so the mock must return some.
    when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
    return chatModel;
  }

  @Bean
  EmbeddingModel embeddingModel() {
    return new EmbeddingModel() {
      @Override
      public EmbeddingResponse call(EmbeddingRequest request) {
        float[] vector = new float[3072];
        Arrays.fill(vector, 0.01f);
        return new EmbeddingResponse(
            request.getInstructions().stream().map(text -> new Embedding(vector, 0)).toList());
      }

      @Override
      public float[] embed(Document document) {
        return call(new EmbeddingRequest(List.of(document.getText()), null)).getResult().getOutput();
      }
    };
  }
}
