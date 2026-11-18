package com.autobook.ai;

import org.springframework.ai.google.genai.embedding.GoogleGenAiEmbeddingConnectionDetails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Connection to the Gemini Developer API for embeddings (the RAG knowledge base).
 *
 * <p>Spring AI's own {@code GoogleGenAiEmbeddingConnectionAutoConfiguration} always creates this bean,
 * even when embeddings are turned off, and without an API key it falls back to Vertex AI and fails
 * startup. It is excluded in {@code application.properties}; this bean exists only when embeddings
 * are enabled ({@code AUTOBOOK_AI_PROVIDER=google-genai}) and uses only the API key, never Vertex AI.
 */
@Configuration
public class GeminiEmbeddingConnectionConfig {

    @Bean
    @ConditionalOnProperty(name = "spring.ai.model.embedding.text", havingValue = "google-genai")
    public GoogleGenAiEmbeddingConnectionDetails googleGenAiEmbeddingConnectionDetails(
            @Value("${spring.ai.google.genai.embedding.api-key:}") String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException(
                    "Gemini embeddings are enabled (AUTOBOOK_AI_PROVIDER=google-genai) but GEMINI_API_KEY is not set.");
        }
        return GoogleGenAiEmbeddingConnectionDetails.builder().apiKey(apiKey).build();
    }
}
