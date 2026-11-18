package com.autobook.ai.chat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

/**
 * Memory of one conversation between one customer and the assistant: the last turns of the
 * dialogue and the slots the assistant most recently offered, so "book the first one" or
 * "book it" can be resolved on a later turn. Callers synchronize on the instance.
 */
public final class Conversation {

    private final long userId;
    private final String conversationId;
    private final List<Message> history = new ArrayList<>();
    private List<SlotOption> lastOfferedSlots = List.of();
    private Instant lastUsed;

    Conversation(long userId, String conversationId, Instant now) {
        this.userId = userId;
        this.conversationId = conversationId;
        this.lastUsed = now;
    }

    public long userId() {
        return userId;
    }

    public String conversationId() {
        return conversationId;
    }

    public List<Message> history() {
        return List.copyOf(history);
    }

    /** Adds one user/assistant turn and keeps only the last {@code maxTurns} turns. */
    public void remember(String userText, String assistantText, int maxTurns) {
        history.add(new UserMessage(userText));
        history.add(new AssistantMessage(assistantText));
        while (history.size() > Math.max(1, maxTurns) * 2) {
            history.remove(0);
            history.remove(0);
        }
    }

    public List<SlotOption> lastOfferedSlots() {
        return lastOfferedSlots;
    }

    public void offeredSlots(List<SlotOption> slots) {
        this.lastOfferedSlots = List.copyOf(slots);
    }

    Instant lastUsed() {
        return lastUsed;
    }

    void touch(Instant now) {
        this.lastUsed = now;
    }
}
