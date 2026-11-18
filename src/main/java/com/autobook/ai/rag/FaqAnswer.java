package com.autobook.ai.rag;

import java.util.List;

/**
 * Answer from the knowledge base.
 *
 * @param answer                    the answer text
 * @param retrievedFromKnowledgeBase {@code true} if relevant sections were retrieved and given to Gemini;
 *                                  {@code false} if nothing relevant was found and no model answer was generated
 * @param sources                   the retrieved sections the answer is based on, most relevant first
 * @param model                     the chat model that wrote the answer, or empty if none was used
 */
public record FaqAnswer(String answer, boolean retrievedFromKnowledgeBase, List<Source> sources, String model) {

    /**
     * @param ref     the citation marker used in the answer, e.g. "[1]"
     * @param section the knowledge-base section
     * @param score   cosine similarity between the question and the section
     */
    public record Source(String ref, String section, double score) {
    }
}
