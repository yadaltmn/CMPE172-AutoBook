package com.autobook.ai;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Registers the AI test doubles as the application's chat and embedding models. The real Google
 * GenAI auto-configuration is switched off in tests (spring.ai.model.*=none).
 */
@TestConfiguration(proxyBeanMethods = false)
public class AiTestModels {

    @Bean
    ScriptedChatModel scriptedChatModel() {
        return new ScriptedChatModel();
    }

    @Bean
    KeywordEmbeddingModel keywordEmbeddingModel() {
        return new KeywordEmbeddingModel();
    }
}
