package com.autobook.ai.chat;

import com.autobook.ai.AiProperties;
import com.autobook.exception.ForbiddenOperationException;
import com.autobook.exception.InvalidRequestException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Bookings and cancellations prepared by the assistant, waiting for the customer's explicit
 * confirmation. A pending action is bound to one customer and one conversation, has a random
 * single-use token, and expires after {@code autobook.ai.pending-action-ttl}. Each conversation has
 * at most one pending action; preparing a new one replaces the old one.
 *
 * <p>This store, not the model, decides whether a write may happen: the model can only create
 * pending actions, and only a confirmation from the same signed-in customer can consume one.
 */
@Component
public class PendingActionStore {

    public enum Type {
        BOOK,
        CANCEL
    }

    public record PendingAction(String token, long userId, String conversationId, Type type, long targetId,
                                String summary, Instant expiresAt) {
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, PendingAction> byToken = new ConcurrentHashMap<>();
    private final Map<String, String> tokenByConversation = new ConcurrentHashMap<>();
    private final AiProperties properties;
    private final Clock clock;

    public PendingActionStore(AiProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public PendingAction create(long userId, String conversationId, Type type, long targetId, String summary) {
        purgeExpired();
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        PendingAction action = new PendingAction(token, userId, conversationId, type, targetId, summary,
                clock.instant().plus(properties.pendingActionTtl()));
        byToken.put(token, action);
        String previous = tokenByConversation.put(conversationKey(userId, conversationId), token);
        if (previous != null) {
            byToken.remove(previous);
        }
        return action;
    }

    /** The conversation's unexpired pending action, if any. */
    public Optional<PendingAction> current(long userId, String conversationId) {
        String token = tokenByConversation.get(conversationKey(userId, conversationId));
        PendingAction action = token == null ? null : byToken.get(token);
        if (action == null || isExpired(action)) {
            return Optional.empty();
        }
        return Optional.of(action);
    }

    /**
     * Consumes a pending action. Succeeds only once, only for the customer and conversation it was
     * created for, and only before it expires.
     */
    public PendingAction claim(String token, long userId, String conversationId) {
        PendingAction action = token == null ? null : byToken.get(token);
        if (action == null) {
            throw new InvalidRequestException("This confirmation is no longer valid. It was already used, replaced, "
                    + "or never existed. Please ask the assistant again.");
        }
        if (action.userId() != userId) {
            // Not consumed: the owner can still confirm it.
            throw new ForbiddenOperationException("This confirmation belongs to another customer.");
        }
        if (!action.conversationId().equals(conversationId)) {
            throw new InvalidRequestException("This confirmation belongs to a different conversation.");
        }
        if (isExpired(action)) {
            remove(action);
            throw new InvalidRequestException("This confirmation has expired. Please ask the assistant again.");
        }
        if (!byToken.remove(token, action)) {
            throw new InvalidRequestException("This confirmation was already used.");
        }
        tokenByConversation.remove(conversationKey(userId, conversationId), token);
        return action;
    }

    private void remove(PendingAction action) {
        byToken.remove(action.token(), action);
        tokenByConversation.remove(conversationKey(action.userId(), action.conversationId()), action.token());
    }

    private boolean isExpired(PendingAction action) {
        return !clock.instant().isBefore(action.expiresAt());
    }

    private void purgeExpired() {
        byToken.values().stream().filter(this::isExpired).toList().forEach(this::remove);
    }

    private static String conversationKey(long userId, String conversationId) {
        return userId + ":" + conversationId;
    }
}
