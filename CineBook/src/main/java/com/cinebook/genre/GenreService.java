package com.cinebook.genre;

import com.cinebook.exception.ConflictException;
import com.cinebook.genre.dto.CreateGenreRequest;
import com.cinebook.genre.dto.GenreDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GenreService {

    private final GenreRepository genreRepository;

    public GenreService(GenreRepository genreRepository) {
        this.genreRepository = genreRepository;
    }

    public List<GenreDto> getAllGenres() {
        return genreRepository.findAll().stream().map(GenreDto::from).toList();
    }

    @Transactional
    public GenreDto createGenre(CreateGenreRequest request) {
        String trimmed = request.name().trim();
        if (genreRepository.findByName(trimmed).isPresent()) {
            throw new ConflictException("Genre '" + trimmed + "' already exists.");
        }
        return GenreDto.from(genreRepository.save(trimmed));
    }
}
