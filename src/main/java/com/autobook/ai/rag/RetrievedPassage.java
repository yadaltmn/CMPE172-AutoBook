package com.autobook.ai.rag;

/**
 * A knowledge-base chunk returned by similarity search, with its cosine similarity to the question.
 */
public record RetrievedPassage(String id, String section, String text, double score) {
}
