package com.rag.assistant.config;

import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.generation.augmentation.QueryAugmenter;

/**
 * Keeps the question in the prompt when no chunk was retrieved.
 *
 * <p>Spring AI's {@code ContextualQueryAugmenter} renders its empty-context template without the
 * question, so the model cannot tell which language to answer in and guesses one. This augmenter
 * renders {@code prompts/no-context.st} with the question and delegates every other case.
 */
record QuestionAwareQueryAugmenter(QueryAugmenter delegate, PromptTemplate noContextPrompt)
    implements QueryAugmenter {

  static final String QUERY = "query";

  @Override
  public Query augment(Query query, List<Document> documents) {
    if (documents.isEmpty()) {
      return new Query(noContextPrompt.render(Map.of(QUERY, query.text())));
    }
    return delegate.augment(query, documents);
  }
}
