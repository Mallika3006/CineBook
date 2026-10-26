package com.cinebook.movie;

import com.cinebook.common.PagedResponse;
import com.cinebook.exception.BadRequestException;
import com.cinebook.exception.ConflictException;
import com.cinebook.exception.ResourceNotFoundException;
import com.cinebook.genre.GenreRepository;
import com.cinebook.movie.dto.MovieDto;
import com.cinebook.movie.dto.MovieRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.List;

@Service
public class MovieService {

    private final MovieRepository movieRepository;
    private final GenreRepository genreRepository;

    public MovieService(MovieRepository movieRepository, GenreRepository genreRepository) {
        this.movieRepository = movieRepository;
        this.genreRepository = genreRepository;
    }

    public PagedResponse<MovieDto> listMovies(
            String search,
            Long genreId,
            String language,
            String releaseStatus,
            boolean includeInactive,
            String sortBy,
            int page,
            int size
    ) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 50);

        List<MovieDto> items = movieRepository.searchMovies(
                search, genreId, language, releaseStatus, includeInactive, sortBy, safePage, safeSize
        ).stream().map(MovieDto::from).toList();

        long total = movieRepository.countMovies(search, genreId, language, releaseStatus, includeInactive);
        return PagedResponse.of(items, safePage, safeSize, total);
    }

    public MovieDto getMovieById(Long id, boolean allowInactive) {
        Movie movie = movieRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Movie not found with ID: " + id));
        if (!allowInactive && !movie.isActive()) {
            throw new ResourceNotFoundException("Movie is not currently active.");
        }
        return MovieDto.from(movie);
    }

    @Transactional
    public MovieDto createMovie(MovieRequest request) {
        validateMovieRequest(request);
        Movie m = mapRequestToMovie(request);
        return MovieDto.from(movieRepository.save(m));
    }

    @Transactional
    public MovieDto updateMovie(Long id, MovieRequest request) {
        movieRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Movie not found with ID: " + id));
        validateMovieRequest(request);
        Movie m = mapRequestToMovie(request);
        return MovieDto.from(movieRepository.update(id, m));
    }

    @Transactional
    public MovieDto updateMovieStatus(Long id, boolean active) {
        movieRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Movie not found with ID: " + id));
        movieRepository.updateStatus(id, active);
        return MovieDto.from(movieRepository.findById(id).orElseThrow());
    }

    @Transactional
    public void deleteMovieSafely(Long id) {
        movieRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Movie not found with ID: " + id));
        if (movieRepository.hasShowtimesOrBookings(id)) {
            throw new ConflictException("Cannot delete a movie referenced by showtimes or historical bookings. Deactivate it instead.");
        }
        movieRepository.deleteById(id);
    }

    private void validateMovieRequest(MovieRequest req) {
        genreRepository.findById(req.genreId())
                .orElseThrow(() -> new BadRequestException("Invalid genre ID: " + req.genreId()));

        validateUrlOrAssetPath(req.posterUrl(), "Poster URL");
        if (req.backdropUrl() != null && !req.backdropUrl().isBlank()) {
            validateUrlOrAssetPath(req.backdropUrl(), "Backdrop URL");
        }
        if (req.trailerUrl() != null && !req.trailerUrl().isBlank()) {
            validateUrlOrAssetPath(req.trailerUrl(), "Trailer URL");
        }

        if (req.releaseStatus() != null && !req.releaseStatus().isBlank()) {
            String rs = req.releaseStatus().trim().toUpperCase();
            if (!rs.equals("NOW_SHOWING") && !rs.equals("COMING_SOON")) {
                throw new BadRequestException("Release status must be NOW_SHOWING or COMING_SOON.");
            }
        }
    }

    private void validateUrlOrAssetPath(String url, String fieldName) {
        String trimmed = url.trim();
        if (trimmed.startsWith("/assets/") || trimmed.startsWith("assets/")) {
            return;
        }
        try {
            URI uri = URI.create(trimmed);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw new BadRequestException(fieldName + " must be a valid HTTP/HTTPS URL or local /assets/ path.");
            }
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(fieldName + " is not a valid URL.");
        }
    }

    private Movie mapRequestToMovie(MovieRequest req) {
        Movie m = new Movie();
        m.setTitle(req.title().trim());
        m.setDescription(req.description().trim());
        m.setPosterUrl(req.posterUrl().trim());
        m.setBackdropUrl(req.backdropUrl() != null && !req.backdropUrl().isBlank()
                ? req.backdropUrl().trim()
                : "/assets/posters/backdrop-1.svg");
        m.setGenreId(req.genreId());
        m.setLanguage(req.language().trim());
        m.setDurationMinutes(req.durationMinutes());
        m.setReleaseDate(req.releaseDate());
        m.setAgeRating(req.ageRating() != null && !req.ageRating().isBlank() ? req.ageRating().trim() : "UA");
        m.setCastDetails(req.castDetails() != null ? req.castDetails().trim() : "");
        m.setDirector(req.director() != null ? req.director().trim() : "");
        m.setTrailerUrl(req.trailerUrl() != null ? req.trailerUrl().trim() : "");
        m.setReleaseStatus(req.releaseStatus() != null && !req.releaseStatus().isBlank()
                ? req.releaseStatus().trim().toUpperCase()
                : "NOW_SHOWING");
        m.setPopularityScore(req.popularityScore() != null ? req.popularityScore() : 85);
        m.setActive(req.active() == null || req.active());
        return m;
    }
}
