package com.autobook.ai;

import com.autobook.exception.AiUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Single access point to the Gemini models. The Spring AI Google GenAI starter creates the
 * {@link ChatModel} and {@link EmbeddingModel} only when {@code AUTOBOOK_AI_PROVIDER=google-genai}
 * (and a {@code GEMINI_API_KEY} is set); otherwise no model beans exist and every AI feature reports
 * itself unavailable instead of failing application startup.
 */
@Component
public class AiModels {

    private static final Logger log = LoggerFactory.getLogger(AiModels.class);

    private final ChatModel chatModel;
    private final EmbeddingModel embeddingModel;
    private final ChatClient chatClient;
    private final String chatModelName;
    private final String embeddingModelName;

    public AiModels(ObjectProvider<ChatModel> chatModels, ObjectProvider<EmbeddingModel> embeddingModels,
                    @Value("${spring.ai.google.genai.chat.model:}") String chatModelName,
                    @Value("${spring.ai.google.genai.embedding.text.model:}") String embeddingModelName) {
        this.chatModel = chatModels.getIfUnique();
        this.embeddingModel = embeddingModels.getIfUnique();
        this.chatClient = chatModel == null ? null : ChatClient.create(chatModel);
        this.chatModelName = chatModelName;
        this.embeddingModelName = embeddingModelName;
    }

    @EventListener(ApplicationReadyEvent.class)
    void logStatus() {
        if (chatModel == null) {
            log.warn("AI chat model not configured: set GEMINI_API_KEY and AUTOBOOK_AI_PROVIDER=google-genai "
                    + "to enable the AutoBook assistant. Booking works without it.");
        } else {
            log.info("AI chat model enabled ({})", chatModelName);
        }
        if (embeddingModel == null) {
            log.warn("AI embedding model not configured: the FAQ knowledge base is unavailable.");
        } else {
            log.info("AI embedding model enabled ({})", embeddingModelName);
        }
    }

    public boolean chatAvailable() {
        return chatClient != null;
    }

    public boolean embeddingAvailable() {
        return embeddingModel != null;
    }

    public ChatClient chatClient() {
        if (chatClient == null) {
            throw new AiUnavailableException("The AI assistant is not available: no Gemini API key is configured.");
        }
        return chatClient;
    }

    public EmbeddingModel embeddingModel() {
        if (embeddingModel == null) {
            throw new AiUnavailableException("The AutoBook knowledge base is not available: no Gemini API key is configured.");
        }
        return embeddingModel;
    }

    public String chatModelName() {
        return chatModelName;
    }

    public String embeddingModelName() {
        return embeddingModelName;
    }
}
