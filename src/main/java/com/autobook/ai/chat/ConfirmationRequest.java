package com.autobook.ai.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The customer's answer to a pending booking or cancellation.
 *
 * @param confirm {@code true} (default) to carry it out, {@code false} to decline it
 */
public record ConfirmationRequest(
        @NotBlank(message = "is required")
        @Size(max = 64, message = "must be at most 64 characters")
        @Pattern(regexp = "[A-Za-z0-9-]*", message = "may contain only letters, digits, and dashes")
        String conversationId,
        @NotBlank(message = "is required")
        @Size(max = 64, message = "must be at most 64 characters")
        String token,
        Boolean confirm
) {
}
