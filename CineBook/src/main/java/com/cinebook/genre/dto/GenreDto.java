package com.cinebook.genre.dto;

import com.cinebook.genre.Genre;

public record GenreDto(Long id, String name) {
    public static GenreDto from(Genre g) {
        return new GenreDto(g.getId(), g.getName());
    }
}
