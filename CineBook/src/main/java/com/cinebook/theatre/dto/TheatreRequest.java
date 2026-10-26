package com.cinebook.theatre.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TheatreRequest(
        @NotBlank(message = "Theatre name is required")
        @Size(max = 180, message = "Theatre name must not exceed 180 characters")
        String name,

        @NotBlank(message = "Theatre address is required")
        @Size(max = 350, message = "Address must not exceed 350 characters")
        String address,

        @NotBlank(message = "City is required")
        @Size(max = 100, message = "City must not exceed 100 characters")
        String city,

        Boolean active
) {
}
