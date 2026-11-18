package com.autobook.ai.chat;

import com.autobook.ai.AiProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * In-memory conversation memory, isolated per customer: the key is (user id, conversation id), so
 * a conversation id guessed or copied by another customer opens an empty conversation, never someone
 * else's. Idle conversations expire after {@code autobook.ai.conversation-ttl}; at most
 * {@value #MAX_CONVERSATIONS} are kept. Memory is not persisted, so it is cleared by a restart.
 */
@Component
public class ConversationStore {

    static final int MAX_CONVERSATIONS = 1000;

    private record Key(long userId, String conversationId) {
    }

    private final Map<Key, Conversation> conversations = new ConcurrentHashMap<>();
    private final AiProperties properties;
    private final Clock clock;

    public ConversationStore(AiProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public Conversation get(long userId, String conversationId) {
        Instant now = clock.instant();
        evictExpired(now);
        Conversation conversation = conversations.computeIfAbsent(new Key(userId, conversationId),
                key -> new Conversation(userId, conversationId, now));
        conversation.touch(now);
        return conversation;
    }

    public int size() {
        return conversations.size();
    }

    private void evictExpired(Instant now) {
        Instant cutoff = now.minus(properties.conversationTtl());
        conversations.values().removeIf(conversation -> conversation.lastUsed().isBefore(cutoff));
        if (conversations.size() >= MAX_CONVERSATIONS) {
            conversations.entrySet().stream()
                    .min(Comparator.comparing(entry -> entry.getValue().lastUsed()))
                    .ifPresent(oldest -> conversations.remove(oldest.getKey()));
        }
    }
}
