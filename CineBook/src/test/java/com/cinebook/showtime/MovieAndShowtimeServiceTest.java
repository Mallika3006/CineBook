package com.cinebook.showtime;

import com.cinebook.booking.BookingRepository;
import com.cinebook.config.AppProperties;
import com.cinebook.exception.BadRequestException;
import com.cinebook.exception.ConflictException;
import com.cinebook.genre.Genre;
import com.cinebook.genre.GenreRepository;
import com.cinebook.movie.Movie;
import com.cinebook.movie.MovieRepository;
import com.cinebook.movie.MovieService;
import com.cinebook.movie.dto.MovieRequest;
import com.cinebook.screen.Screen;
import com.cinebook.screen.ScreenRepository;
import com.cinebook.seat.SeatRepository;
import com.cinebook.showtime.dto.ShowtimeDto;
import com.cinebook.showtime.dto.ShowtimeRequest;
import com.cinebook.theatre.Theatre;
import com.cinebook.theatre.TheatreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MovieAndShowtimeServiceTest {

    @Mock
    private MovieRepository movieRepository;
    @Mock
    private GenreRepository genreRepository;
    @Mock
    private ShowtimeRepository showtimeRepository;
    @Mock
    private ScreenRepository screenRepository;
    @Mock
    private TheatreRepository theatreRepository;
    @Mock
    private SeatRepository seatRepository;
    @Mock
    private BookingRepository bookingRepository;

    private MovieService movieService;
    private ShowtimeService showtimeService;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        props.getShowtime().setTurnaroundBufferMinutes(25);
        movieService = new MovieService(movieRepository, genreRepository);
        showtimeService = new ShowtimeService(
                showtimeRepository,
                movieRepository,
                screenRepository,
                theatreRepository,
                seatRepository,
                bookingRepository,
                props
        );
    }

    @Test
    @DisplayName("Deleting a movie referenced by showtimes or bookings is rejected with ConflictException")
    void preventDeletingReferencedMovie() {
        Movie m = new Movie();
        m.setId(1L);
        m.setTitle("Chronos Protocol");
        when(movieRepository.findById(1L)).thenReturn(Optional.of(m));
        when(movieRepository.hasShowtimesOrBookings(1L)).thenReturn(true);

        assertThatThrownBy(() -> movieService.deleteMovieSafely(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Cannot delete a movie referenced by showtimes");
    }

    @Test
    @DisplayName("Movie creation rejects invalid poster URL scheme")
    void rejectInvalidPosterUrl() {
        when(genreRepository.findById(1L)).thenReturn(Optional.of(new Genre(1L, "Sci-Fi")));
        MovieRequest req = new MovieRequest(
                "Test Movie",
                "Synopsis",
                "javascript:alert(1)",
                null,
                1L,
                "English",
                120,
                LocalDate.now(),
                "UA",
                "Cast",
                "Director",
                null,
                "NOW_SHOWING",
                90,
                true
        );

        assertThatThrownBy(() -> movieService.createMovie(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Poster URL");
    }

    @Test
    @DisplayName("Scheduling an overlapping showtime on the same screen is rejected with ConflictException")
    void rejectOverlappingShowtimeOnSameScreen() {
        Movie activeMovie = new Movie();
        activeMovie.setId(1L);
        activeMovie.setTitle("Chronos Protocol");
        activeMovie.setDurationMinutes(150);
        activeMovie.setActive(true);

        Screen activeScreen = new Screen();
        activeScreen.setId(5L);
        activeScreen.setTheatreId(2L);
        activeScreen.setName("Audi 1 — IMAX");
        activeScreen.setCapacity(80);
        activeScreen.setActive(true);

        Theatre activeTheatre = new Theatre();
        activeTheatre.setId(2L);
        activeTheatre.setName("CineBook Luxe");
        activeTheatre.setActive(true);

        OffsetDateTime futureStart = OffsetDateTime.now().plusDays(2).withHour(18).withMinute(0);

        when(movieRepository.findById(1L)).thenReturn(Optional.of(activeMovie));
        when(screenRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(activeScreen));
        when(theatreRepository.findById(2L)).thenReturn(Optional.of(activeTheatre));
        when(showtimeRepository.hasOverlappingShowtime(eq(5L), eq(futureStart), any(OffsetDateTime.class), eq(null)))
                .thenReturn(true);

        ShowtimeRequest req = new ShowtimeRequest(
                1L,
                5L,
                futureStart,
                "IMAX 2D",
                new BigDecimal("280.00"),
                new BigDecimal("380.00"),
                new BigDecimal("520.00")
        );

        assertThatThrownBy(() -> showtimeService.createShowtime(req))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("overlapping showtime");
    }

    @Test
    @DisplayName("Scheduling a showtime in the past is rejected with BadRequestException")
    void rejectShowtimeInPast() {
        Movie activeMovie = new Movie();
        activeMovie.setId(1L);
        activeMovie.setDurationMinutes(120);
        activeMovie.setActive(true);

        Screen activeScreen = new Screen();
        activeScreen.setId(5L);
        activeScreen.setTheatreId(2L);
        activeScreen.setActive(true);

        Theatre activeTheatre = new Theatre();
        activeTheatre.setId(2L);
        activeTheatre.setActive(true);

        when(movieRepository.findById(1L)).thenReturn(Optional.of(activeMovie));
        when(screenRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(activeScreen));
        when(theatreRepository.findById(2L)).thenReturn(Optional.of(activeTheatre));

        ShowtimeRequest pastReq = new ShowtimeRequest(
                1L,
                5L,
                OffsetDateTime.now().minusHours(2),
                "2D",
                new BigDecimal("200.00"),
                new BigDecimal("280.00"),
                new BigDecimal("400.00")
        );

        assertThatThrownBy(() -> showtimeService.createShowtime(pastReq))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("future");
    }

    @Test
    @DisplayName("Showtime end time is calculated automatically using movie duration + turnaround buffer")
    void calculateShowtimeEndTimeWithBuffer() {
        Movie activeMovie = new Movie();
        activeMovie.setId(1L);
        activeMovie.setTitle("Chronos Protocol");
        activeMovie.setDurationMinutes(140);
        activeMovie.setActive(true);

        Screen activeScreen = new Screen();
        activeScreen.setId(5L);
        activeScreen.setTheatreId(2L);
        activeScreen.setName("Audi 1");
        activeScreen.setCapacity(80);
        activeScreen.setActive(true);

        Theatre activeTheatre = new Theatre();
        activeTheatre.setId(2L);
        activeTheatre.setName("CineBook Vega City");
        activeTheatre.setActive(true);

        OffsetDateTime start = OffsetDateTime.now().plusDays(1).withHour(14).withMinute(0).withSecond(0).withNano(0);
        OffsetDateTime expectedEnd = start.plusMinutes(140 + 25);

        when(movieRepository.findById(1L)).thenReturn(Optional.of(activeMovie));
        when(screenRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(activeScreen));
        when(theatreRepository.findById(2L)).thenReturn(Optional.of(activeTheatre));
        when(showtimeRepository.hasOverlappingShowtime(eq(5L), eq(start), eq(expectedEnd), eq(null))).thenReturn(false);
        when(showtimeRepository.save(any(Showtime.class))).thenAnswer(inv -> {
            Showtime st = inv.getArgument(0);
            st.setId(99L);
            st.setScreenCapacity(80);
            st.setBookedSeatCount(0);
            return st;
        });

        ShowtimeDto created = showtimeService.createShowtime(new ShowtimeRequest(
                1L, 5L, start, "IMAX 2D",
                new BigDecimal("250.00"), new BigDecimal("350.00"), new BigDecimal("480.00")
        ));

        assertThat(created.endTime()).isEqualTo(expectedEnd);
        assertThat(created.availableSeats()).isEqualTo(80);
    }
}
