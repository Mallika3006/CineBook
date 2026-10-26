package com.cinebook.booking;

import com.cinebook.booking.dto.BookingDto;
import com.cinebook.booking.dto.CreateBookingRequest;
import com.cinebook.exception.ConflictException;
import com.cinebook.movie.MovieService;
import com.cinebook.report.ReportService;
import com.cinebook.report.dto.DashboardSummaryDto;
import com.cinebook.seat.dto.ShowtimeSeatMapResponse;
import com.cinebook.showtime.ShowtimeService;
import com.cinebook.showtime.dto.ShowtimeDto;
import com.cinebook.user.User;
import com.cinebook.user.UserRepository;
import com.cinebook.user.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-stack PostgreSQL + Flyway + Spring JDBC Integration Test using Testcontainers.
 * Automatically runs when Docker Desktop is active; gracefully skipped if Docker daemon is offline.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class PostgresContainerIntegrationIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("cinebook_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private MovieService movieService;
    @Autowired
    private ShowtimeService showtimeService;
    @Autowired
    private BookingService bookingService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ReportService reportService;

    @Test
    @DisplayName("Flyway V1 & V2 migrations initialize clean PostgreSQL database and enforce concurrent seat booking uniqueness")
    void verifyFlywayMigrationsAndConcurrentSeatBookingAgainstPostgres() throws Exception {
        var moviesPage = movieService.listMovies(null, null, null, "NOW_SHOWING", false, "popularity", 0, 20);
        assertThat(moviesPage.totalElements()).isGreaterThanOrEqualTo(8);

        List<ShowtimeDto> showtimes = showtimeService.listShowtimes(null, "Bengaluru", null, null, "SCHEDULED");
        assertThat(showtimes).isNotEmpty();

        ShowtimeDto targetShowtime = showtimes.getFirst();
        ShowtimeSeatMapResponse seatMap = showtimeService.getShowtimeSeatMap(targetShowtime.id());
        assertThat(seatMap.seats()).hasSize(80);
        Long targetSeatId = seatMap.seats().getFirst().id();

        // Create 8 concurrent test customers
        int numCustomers = 8;
        List<Long> customerIds = new ArrayList<>();
        for (int i = 0; i < numCustomers; i++) {
            User u = new User();
            u.setFullName("Concurrent Customer " + i);
            u.setEmail("concurrent" + i + "@cinebook.local");
            u.setPasswordHash("$2a$10$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ01234");
            u.setRole(UserRole.USER);
            u.setActive(true);
            customerIds.add(userRepository.save(u).getId());
        }

        ExecutorService pool = Executors.newFixedThreadPool(numCustomers);
        CountDownLatch ready = new CountDownLatch(numCustomers);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger winners = new AtomicInteger(0);
        AtomicInteger conflicts = new AtomicInteger(0);
        List<BookingDto> successfulBookings = new ArrayList<>();

        List<Callable<Void>> tasks = new ArrayList<>();
        for (Long userId : customerIds) {
            tasks.add(() -> {
                ready.countDown();
                start.await();
                try {
                    BookingDto b = bookingService.createBooking(
                            userId,
                            new CreateBookingRequest(targetShowtime.id(), List.of(targetSeatId), null),
                            null
                    );
                    synchronized (successfulBookings) {
                        successfulBookings.add(b);
                    }
                    winners.incrementAndGet();
                } catch (ConflictException ex) {
                    conflicts.incrementAndGet();
                }
                return null;
            });
        }

        List<Future<Void>> futures = new ArrayList<>();
        for (Callable<Void> t : tasks) {
            futures.add(pool.submit(t));
        }
        ready.await();
        start.countDown();
        for (Future<Void> f : futures) {
            f.get();
        }
        pool.shutdown();

        assertThat(winners.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(numCustomers - 1);

        // Verify cancellation releases the seat and allows re-booking while preserving history
        BookingDto winningBooking = successfulBookings.getFirst();
        BookingDto cancelled = bookingService.cancelBooking(winningBooking.id(), winningBooking.userId(), false);
        assertThat(cancelled.status()).isEqualTo("CANCELLED");

        BookingDto rebooked = bookingService.createBooking(
                customerIds.getLast(),
                new CreateBookingRequest(targetShowtime.id(), List.of(targetSeatId), null),
                null
        );
        assertThat(rebooked.status()).isEqualTo("CONFIRMED");

        DashboardSummaryDto summary = reportService.getSummary();
        assertThat(summary.confirmedBookings()).isGreaterThanOrEqualTo(1L);
        assertThat(summary.cancelledBookings()).isGreaterThanOrEqualTo(1L);
    }
}
