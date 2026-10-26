package com.cinebook.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 120, message = "Full name must be between 2 and 120 characters")
        String fullName,

        @Size(min = 8, max = 72, message = "New password must be between 8 and 72 characters")
        String newPassword,

        String currentPassword
) {
}
