package com.rag.assistant;

import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/** Builds the {@link ChatResponse} objects returned by the mocked chat model. */
public final class ChatResponses {

  private ChatResponses() {}

  public static ChatResponse of(String text) {
    return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
  }

  /** Whether the prompt is the query rewrite of {@code prompts/rewrite.st} rather than the final answer. */
  public static boolean isRewrite(Prompt prompt) {
    return prompt.getUserMessage().getText().contains("Rewritten question:");
  }
}
