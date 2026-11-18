package com.autobook.ai.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A chat message from the signed-in customer. There is deliberately no user id field: the
 * assistant always acts as the authenticated user.
 *
 * @param conversationId optional; omit to start a new conversation
 */
public record ChatRequest(
        @Size(max = 64, message = "must be at most 64 characters")
        @Pattern(regexp = "[A-Za-z0-9-]*", message = "may contain only letters, digits, and dashes")
        String conversationId,
        @NotBlank(message = "is required")
        @Size(max = 1000, message = "must be at most 1000 characters")
        String message
) {
}
