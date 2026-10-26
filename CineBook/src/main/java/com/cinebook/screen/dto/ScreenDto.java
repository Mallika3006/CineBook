package com.cinebook.screen.dto;

import com.cinebook.screen.Screen;

public record ScreenDto(
        Long id,
        Long theatreId,
        String theatreName,
        String city,
        String name,
        int capacity,
        boolean active
) {
    public static ScreenDto from(Screen s) {
        return new ScreenDto(
                s.getId(),
                s.getTheatreId(),
                s.getTheatreName(),
                s.getCity(),
                s.getName(),
                s.getCapacity(),
                s.isActive()
        );
    }
}
