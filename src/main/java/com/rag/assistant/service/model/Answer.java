package com.rag.assistant.service.model;

import java.util.List;

/** Answer to a question and the pages it was based on, in order of relevance and without duplicates. */
public record Answer(String text, List<Source> sources) {}
