package com.autobook.ai.rag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FaqRequest(
        @NotBlank(message = "is required")
        @Size(max = 500, message = "must be at most 500 characters")
        String question
) {
}
