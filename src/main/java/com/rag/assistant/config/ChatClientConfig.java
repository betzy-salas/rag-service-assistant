package com.rag.assistant.config;

import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;
import org.springframework.ai.rag.preretrieval.query.expansion.QueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

/**
 * Builds the {@link ChatClient} with the Spring AI RAG pipeline: searches with the question and its
 * rewrite, retrieves chunks from the vector store and injects them in the prompt before calling the
 * chat model.
 */
@Configuration
public class ChatClientConfig {

  @Bean
  public ChatClient chatClient(
      ChatClient.Builder builder,
      VectorStore vectorStore,
      RagProperties properties,
      @Value(PromptPaths.SYSTEM) Resource systemPrompt,
      @Value(PromptPaths.REWRITE) Resource rewritePrompt,
      @Value(PromptPaths.RAG) Resource ragPrompt,
      @Value(PromptPaths.NO_CONTEXT) Resource noContextPrompt) {
    // Short questions or questions with typos score below the similarity threshold, so the chat
    // model normalizes them first. The transformer gets its own client, without the RAG advisor.
    var rewriter =
        RewriteQueryTransformer.builder()
            .chatClientBuilder(builder.clone())
            .promptTemplate(new PromptTemplate(rewritePrompt))
            .build();
    // The rewrite can also change the meaning (an ambiguous verb read as another one), so the
    // original question is searched as well: the rewrite can add chunks but never lose them.
    QueryExpander expander =
        query -> {
          Query rewritten = rewriter.transform(query);
          return rewritten.text().equals(query.text()) ? List.of(query) : List.of(query, rewritten);
        };
    var retriever =
        VectorStoreDocumentRetriever.builder()
            .vectorStore(vectorStore)
            .topK(properties.topK())
            .similarityThreshold(properties.similarityThreshold())
            .build();
    // The joiner merges both searches sorted by score, so the context is capped again at top-k.
    DocumentPostProcessor topK = (query, documents) -> documents.stream().limit(properties.topK()).toList();
    // Without context the model gets a prompt that tells it to admit it, instead of answering from
    // its own knowledge.
    var augmenter =
        new QuestionAwareQueryAugmenter(
            ContextualQueryAugmenter.builder().promptTemplate(new PromptTemplate(ragPrompt)).build(),
            new PromptTemplate(noContextPrompt));
    var advisor =
        RetrievalAugmentationAdvisor.builder()
            .queryExpander(expander)
            .documentRetriever(retriever)
            .documentPostProcessors(topK)
            .queryAugmenter(augmenter)
            .build();
    return builder.defaultSystem(systemPrompt).defaultAdvisors(advisor).build();
  }
}
