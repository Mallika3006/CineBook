package com.cinebook.auth.dto;

import com.cinebook.user.dto.UserDto;

public record AuthResponse(
        String token,
        String tokenType,
        long expiresInMs,
        UserDto user
) {
}
