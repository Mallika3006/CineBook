package com.cinebook.user.dto;

import com.cinebook.user.User;
import java.time.OffsetDateTime;

public record UserDto(
        Long id,
        String fullName,
        String email,
        String role,
        boolean active,
        OffsetDateTime createdAt
) {
    public static UserDto from(User u) {
        return new UserDto(
                u.getId(),
                u.getFullName(),
                u.getEmail(),
                u.getRole().name(),
                u.isActive(),
                u.getCreatedAt()
        );
    }
}
