package com.cinebook.movie.dto;

import jakarta.validation.constraints.NotNull;

public record MovieStatusRequest(
        @NotNull(message = "Active status is required")
        Boolean active
) {
}
