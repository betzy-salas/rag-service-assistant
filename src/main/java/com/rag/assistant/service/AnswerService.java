package com.rag.assistant.service;

import com.rag.assistant.service.mapper.SourceMapper;
import com.rag.assistant.service.model.Answer;
import com.rag.assistant.service.model.ChunkMetadata;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Answers questions with the RAG pipeline configured in the {@link ChatClient}, citing the pages used. */
@Service
@RequiredArgsConstructor
public class AnswerService {

  private final ChatClient chatClient;
  private final SourceMapper sourceMapper;

  /**
   * Retrieves the chunks relevant to the question, asks the chat model and returns the answer with
   * its sources.
   *
   * @param spaceKey if present, only chunks of that Confluence space are retrieved
   */
  public Answer answer(String question, @Nullable String spaceKey) {
    ChatClientResponse response =
        chatClient
            .prompt()
            .user(question)
            .advisors(
                advisors -> {
                  if (StringUtils.hasText(spaceKey)) {
                    advisors.param(
                        VectorStoreDocumentRetriever.FILTER_EXPRESSION,
                        new FilterExpressionBuilder().eq(ChunkMetadata.SPACE_KEY, spaceKey).build());
                  }
                })
            .call()
            .chatClientResponse();
    return new Answer(text(response.chatResponse()), sourceMapper.toSources(retrievedChunks(response)));
  }

  private static String text(@Nullable ChatResponse chatResponse) {
    if (chatResponse == null || chatResponse.getResult() == null) {
      return "";
    }
    String text = chatResponse.getResult().getOutput().getText();
    return text == null ? "" : text;
  }

  @SuppressWarnings("unchecked")
  private static List<Document> retrievedChunks(ChatClientResponse response) {
    Object chunks = response.context().get(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT);
    return chunks instanceof List<?> list ? (List<Document>) list : List.of();
  }
}
