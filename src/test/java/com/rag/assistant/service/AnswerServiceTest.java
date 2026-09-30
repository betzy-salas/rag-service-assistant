package com.rag.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rag.assistant.ChatResponses;
import com.rag.assistant.config.ChatClientConfig;
import com.rag.assistant.config.PromptPaths;
import com.rag.assistant.config.RagProperties;
import com.rag.assistant.service.mapper.SourceMapperImpl;
import com.rag.assistant.service.model.Answer;
import com.rag.assistant.service.model.ChunkMetadata;
import com.rag.assistant.service.model.Source;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

/** Exercises the real RAG pipeline of {@link ChatClientConfig} with a mocked vector store and chat model. */
class AnswerServiceTest {

  private final ChatModel chatModel = mock(ChatModel.class);
  private final VectorStore vectorStore = mock(VectorStore.class);
  private static final String REWRITTEN = "¿Por qué usamos OWASP?";
  private AnswerService answerService;

  @BeforeEach
  void setUp() {
    when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
    ChatClient chatClient =
        new ChatClientConfig()
            .chatClient(
                ChatClient.builder(chatModel),
                vectorStore,
                new RagProperties(3, 0.5),
                resource(PromptPaths.SYSTEM),
                resource(PromptPaths.REWRITE),
                resource(PromptPaths.RAG),
                resource(PromptPaths.NO_CONTEXT));
    answerService = new AnswerService(chatClient, new SourceMapperImpl());
    // The model is called twice per question: first to rewrite it, then to answer it.
    when(chatModel.call(any(Prompt.class)))
        .thenAnswer(
            invocation ->
                ChatResponses.of(
                    ChatResponses.isRewrite(invocation.getArgument(0)) ? REWRITTEN : "La respuesta"));
  }

  @Test
  void searchesWithTheOriginalAndTheRewrittenQuestion() {
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

    answerService.answer("¿Porque usamos owaps?", null);

    assertThat(searchRequests())
        .extracting(SearchRequest::getQuery)
        .containsExactlyInAnyOrder("¿Porque usamos owaps?", REWRITTEN);
    assertThat(rewritePrompt().getUserMessage().getText()).contains("¿Porque usamos owaps?");
  }

  @Test
  void searchesOnceWhenTheRewriteDoesNotChangeTheQuestion() {
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

    answerService.answer(REWRITTEN, null);

    assertThat(searchRequests()).extracting(SearchRequest::getQuery).containsExactly(REWRITTEN);
  }

  @Test
  void keepsTheChunksFoundByEitherQuestionUpToTopK() {
    // The rewrite lost the chunk of page A, but the original question still finds it.
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenAnswer(
            invocation ->
                invocation.getArgument(0, SearchRequest.class).getQuery().equals(REWRITTEN)
                    ? List.of(chunk("2", "Página B", "texto b", 0.6), chunk("3", "Página C", "texto c", 0.55))
                    : List.of(chunk("1", "Página A", "texto a", 0.7), chunk("2", "Página B", "texto b", 0.6), chunk("4", "Página D", "texto d", 0.5)));

    Answer answer = answerService.answer("¿Como roto las credenciales?", null);

    assertThat(answer.sources())
        .extracting(Source::title)
        .containsExactly("Página A", "Página B", "Página C");
    assertThat(sentPrompt().getUserMessage().getText()).contains("texto a").doesNotContain("texto d");
  }

  @Test
  void answersWithRetrievedChunksInThePrompt() {
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenReturn(List.of(chunk("1", "Guía de despliegue", "El despliegue se hace con Jenkins", 0.9)));

    Answer answer = answerService.answer("¿Cómo se despliega?", null);

    assertThat(answer.text()).isEqualTo("La respuesta");
    // The rewritten question is only used to search; the model answers the original one.
    Prompt prompt = sentPrompt();
    assertThat(prompt.getSystemMessage().getText()).contains("Confluence");
    assertThat(prompt.getUserMessage().getText())
        .contains("El despliegue se hace con Jenkins")
        .contains("¿Cómo se despliega?")
        .doesNotContain(REWRITTEN);
  }

  @Test
  void citesEachPageOnceInOrderOfRelevance() {
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenReturn(
            List.of(
                chunk("1", "Página A", "texto a", 0.7),
                chunk("2", "Página B", "texto b1", 0.9),
                chunk("2", "Página B", "texto b2", 0.8)));

    Answer answer = answerService.answer("pregunta", null);

    assertThat(answer.sources())
        .containsExactly(
            new Source("Página B", "https://wiki/pages/2"), new Source("Página A", "https://wiki/pages/1"));
  }

  @Test
  void withoutChunksAsksTheModelToAdmitItAndCitesNothing() {
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

    Answer answer = answerService.answer("¿Cuál es el horario?", null);

    assertThat(answer.sources()).isEmpty();
    // The question stays in the prompt so the model answers in its language.
    assertThat(sentPrompt().getUserMessage().getText())
        .contains("does not contain information")
        .contains("¿Cuál es el horario?");
  }

  @Test
  void usesRetrievalSettingsAndFiltersBySpaceKey() {
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

    answerService.answer("pregunta", "DS");

    assertThat(searchRequests())
        .hasSize(2)
        .allSatisfy(
            request -> {
              assertThat(request.getTopK()).isEqualTo(3);
              assertThat(request.getSimilarityThreshold()).isEqualTo(0.5);
              assertThat(request.getFilterExpression())
                  .hasToString("Expression[type=EQ, left=Key[key=spaceKey], right=Value[value=DS]]");
            });
  }

  @Test
  void withoutSpaceKeyDoesNotFilter() {
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

    answerService.answer("pregunta", " ");

    assertThat(searchRequests()).extracting(SearchRequest::getFilterExpression).containsOnlyNulls();
  }

  private List<SearchRequest> searchRequests() {
    ArgumentCaptor<SearchRequest> request = ArgumentCaptor.forClass(SearchRequest.class);
    verify(vectorStore, atLeastOnce()).similaritySearch(request.capture());
    return request.getAllValues();
  }

  private Prompt rewritePrompt() {
    return sentPrompts().getFirst();
  }

  private Prompt sentPrompt() {
    return sentPrompts().getLast();
  }

  private List<Prompt> sentPrompts() {
    ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
    verify(chatModel, times(2)).call(prompt.capture());
    return prompt.getAllValues();
  }

  private static Resource resource(String location) {
    return new DefaultResourceLoader().getResource(location);
  }

  private static Document chunk(String pageId, String title, String text, double score) {
    // Stable ids, like the ones in pgvector, so the same chunk found by both searches is joined once.
    return Document.builder()
        .id(pageId + ":" + text)
        .text(text)
        .score(score)
        .metadata(
            Map.of(
                ChunkMetadata.PAGE_ID, pageId,
                ChunkMetadata.SPACE_KEY, "DS",
                ChunkMetadata.TITLE, title,
                ChunkMetadata.URL, "https://wiki/pages/" + pageId))
        .build();
  }
}
