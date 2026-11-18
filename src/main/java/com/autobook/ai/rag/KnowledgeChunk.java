package com.autobook.ai.rag;

/**
 * One retrievable piece of the knowledge base: a "## " section of the knowledge file.
 *
 * @param id      stable id, e.g. "autobook-faq.md#cancellation-policy"
 * @param section the section heading, used as the source reference shown to users
 * @param text    the text that is embedded and given to Gemini (heading plus body)
 */
public record KnowledgeChunk(String id, String section, String text) {
}
