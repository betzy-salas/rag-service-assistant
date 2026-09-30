package com.rag.assistant.service.mapper;

import com.rag.assistant.service.model.ChunkMetadata;
import com.rag.assistant.service.model.Source;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.springframework.ai.document.Document;

/**
 * Turns the retrieved chunks into the pages to cite as sources.
 *
 * <p>Written by hand because MapStruct cannot read the metadata map of a {@link Document}.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface SourceMapper {

  /** Returns one source per page, most similar page first. */
  default List<Source> toSources(List<Document> chunks) {
    // The advisor's document joiner does not keep the retrieval order, so the chunks are sorted by
    // their similarity score again; chunks without score go last.
    Map<Object, Source> sourcesByPage = new LinkedHashMap<>();
    chunks.stream()
        .sorted(Comparator.comparing(Document::getScore, Comparator.nullsLast(Comparator.reverseOrder())))
        .forEach(chunk -> sourcesByPage.putIfAbsent(chunk.getMetadata().get(ChunkMetadata.PAGE_ID), toSource(chunk)));
    return List.copyOf(sourcesByPage.values());
  }

  default Source toSource(Document chunk) {
    Map<String, Object> metadata = chunk.getMetadata();
    return new Source(
        Objects.toString(metadata.get(ChunkMetadata.TITLE), null),
        Objects.toString(metadata.get(ChunkMetadata.URL), null));
  }
}
