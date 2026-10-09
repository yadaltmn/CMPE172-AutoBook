package com.autobook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProviderUpdateRequest(
        @NotBlank(message = "is required")
        @Size(max = 150, message = "must be at most 150 characters")
        String name,

        @Pattern(regexp = "^$|^[0-9()+\\- ]{7,30}$", message = "must be a valid phone number")
        String phone
) {
}
