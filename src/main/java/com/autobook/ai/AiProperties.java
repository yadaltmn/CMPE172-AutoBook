package com.autobook.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Limits and settings for AutoBook's AI features ({@code autobook.ai.*}).
 *
 * @param maxMessageLength        longest chat message or FAQ question accepted, in characters
 * @param maxToolCallsPerMessage  tool calls the model may make while answering one chat message
 * @param conversationTurns       user/assistant turns kept as conversation memory
 * @param conversationTtl         idle time after which a conversation is forgotten
 * @param pendingActionTtl        how long a booking or cancellation waits for the user's confirmation
 * @param faq                     retrieval settings for the knowledge base
 * @param reminders               settings for the scheduled reminder agent
 */
@ConfigurationProperties("autobook.ai")
public record AiProperties(
        @DefaultValue("1000") int maxMessageLength,
        @DefaultValue("8") int maxToolCallsPerMessage,
        @DefaultValue("10") int conversationTurns,
        @DefaultValue("30m") Duration conversationTtl,
        @DefaultValue("5m") Duration pendingActionTtl,
        @DefaultValue Faq faq,
        @DefaultValue Reminders reminders
) {

    /**
     * @param topK                 chunks retrieved per question
     * @param similarityThreshold  minimum cosine similarity for a chunk to count as relevant
     * @param knowledgeSource      the knowledge base file
     */
    public record Faq(
            @DefaultValue("4") int topK,
            @DefaultValue("0.6") double similarityThreshold,
            @DefaultValue("classpath:knowledge/autobook-faq.md") String knowledgeSource
    ) {
    }

    /**
     * @param enabled           run the reminder job on its schedule
     * @param window            remind appointments starting within this time from now
     * @param batchSize         most appointments handled per run
     * @param deferIfAiFailsUntil if Gemini fails and the appointment is further away than this, wait
     *                          for a later run instead of sending the template reminder
     */
    public record Reminders(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("24h") Duration window,
            @DefaultValue("25") int batchSize,
            @DefaultValue("6h") Duration deferIfAiFailsUntil
    ) {
    }
}
