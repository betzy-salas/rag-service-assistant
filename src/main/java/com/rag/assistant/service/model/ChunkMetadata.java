package com.rag.assistant.service.model;

/** Metadata keys stored with every chunk by the ingestion service. They are a contract between both services. */
public final class ChunkMetadata {

  public static final String PAGE_ID = "pageId";
  public static final String SPACE_KEY = "spaceKey";
  public static final String TITLE = "title";
  public static final String URL = "url";

  private ChunkMetadata() {}
}
