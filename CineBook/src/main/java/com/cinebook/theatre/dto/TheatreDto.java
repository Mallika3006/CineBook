package com.cinebook.theatre.dto;

import com.cinebook.theatre.Theatre;
import java.time.OffsetDateTime;

public record TheatreDto(
        Long id,
        String name,
        String address,
        String city,
        boolean active,
        OffsetDateTime createdAt
) {
    public static TheatreDto from(Theatre t) {
        return new TheatreDto(
                t.getId(),
                t.getName(),
                t.getAddress(),
                t.getCity(),
                t.isActive(),
                t.getCreatedAt()
        );
    }
}
