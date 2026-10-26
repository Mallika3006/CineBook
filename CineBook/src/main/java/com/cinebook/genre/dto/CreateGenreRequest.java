package com.cinebook.genre.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateGenreRequest(
        @NotBlank(message = "Genre name is required")
        @Size(min = 2, max = 80, message = "Genre name must be between 2 and 80 characters")
        String name
) {
}
