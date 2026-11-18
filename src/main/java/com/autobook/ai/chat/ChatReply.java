package com.autobook.ai.chat;

import java.util.List;

/**
 * The assistant's answer.
 *
 * @param conversationId send this back with the next message to continue the conversation
 * @param reply          the assistant's text
 * @param toolsUsed      tools the model called while answering, in order
 * @param suggestedSlots slots the assistant found in this turn (for the website to display)
 * @param pendingAction  a booking or cancellation waiting for the customer's explicit confirmation, or null
 */
public record ChatReply(
        String conversationId,
        String reply,
        List<String> toolsUsed,
        List<SlotOption> suggestedSlots,
        PendingActionView pendingAction
) {

    /**
     * @param token     single-use confirmation token, valid only for this customer and conversation
     * @param type      BOOK or CANCEL
     * @param summary   what will happen on confirmation
     * @param expiresAt ISO date-time after which the token is rejected
     */
    public record PendingActionView(String token, String type, String summary, String expiresAt) {
    }
}
