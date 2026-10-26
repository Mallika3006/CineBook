package com.cinebook.movie;

import com.cinebook.common.ApiResponse;
import com.cinebook.common.PagedResponse;
import com.cinebook.movie.dto.MovieDto;
import com.cinebook.movie.dto.MovieRequest;
import com.cinebook.movie.dto.MovieStatusRequest;
import com.cinebook.showtime.ShowtimeService;
import com.cinebook.showtime.dto.ShowtimeDto;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
public class MovieController {

    private final MovieService movieService;
    private final ShowtimeService showtimeService;

    public MovieController(MovieService movieService, ShowtimeService showtimeService) {
        this.movieService = movieService;
        this.showtimeService = showtimeService;
    }

    @GetMapping("/api/movies")
    public ResponseEntity<PagedResponse<MovieDto>> listMovies(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long genreId,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String releaseStatus,
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @RequestParam(defaultValue = "popularity") String sortBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            Authentication auth
    ) {
        boolean isAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return ResponseEntity.ok(
                movieService.listMovies(search, genreId, language, releaseStatus, includeInactive && isAdmin, sortBy, page, size)
        );
    }

    @GetMapping("/api/movies/{id}")
    public ResponseEntity<MovieDto> getMovie(@PathVariable Long id, Authentication auth) {
        boolean isAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return ResponseEntity.ok(movieService.getMovieById(id, isAdmin));
    }

    @GetMapping("/api/movies/{id}/showtimes")
    public ResponseEntity<List<ShowtimeDto>> getMovieShowtimes(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String city
    ) {
        LocalDate targetDate = date != null ? date : LocalDate.now();
        return ResponseEntity.ok(showtimeService.listShowtimes(targetDate, city, id, null, "SCHEDULED"));
    }

    @PostMapping("/api/admin/movies")
    public ResponseEntity<MovieDto> createMovie(@Valid @RequestBody MovieRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(movieService.createMovie(request));
    }

    @PutMapping("/api/admin/movies/{id}")
    public ResponseEntity<MovieDto> updateMovie(
            @PathVariable Long id,
            @Valid @RequestBody MovieRequest request
    ) {
        return ResponseEntity.ok(movieService.updateMovie(id, request));
    }

    @PatchMapping("/api/admin/movies/{id}/status")
    public ResponseEntity<MovieDto> updateMovieStatus(
            @PathVariable Long id,
            @Valid @RequestBody MovieStatusRequest request
    ) {
        return ResponseEntity.ok(movieService.updateMovieStatus(id, request.active()));
    }

    @DeleteMapping("/api/admin/movies/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteMovie(@PathVariable Long id) {
        movieService.deleteMovieSafely(id);
        return ResponseEntity.ok(ApiResponse.ok("Movie deleted successfully", null));
    }
}
