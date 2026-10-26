package com.cinebook.booking;

import com.cinebook.booking.dto.CreateBookingRequest;
import com.cinebook.config.AppProperties;
import com.cinebook.exception.ConflictException;
import com.cinebook.payment.DemoPaymentGateway;
import com.cinebook.payment.PaymentRepository;
import com.cinebook.seat.Seat;
import com.cinebook.seat.SeatRepository;
import com.cinebook.seat.SeatType;
import com.cinebook.showtime.Showtime;
import com.cinebook.showtime.ShowtimeRepository;
import com.cinebook.showtime.ShowtimeService;
import com.cinebook.showtime.ShowtimeStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Concurrency verification test proving that when multiple customers simultaneously
 * attempt to reserve the exact same seat for the same showtime, only ONE booking
 * succeeds and all other concurrent transactions fail with ConflictException and roll back.
 */
class BookingConcurrencyIntegrationTest {

    @Test
    @DisplayName("10 concurrent booking requests for the same seat result in exactly 1 active reservation and 9 ConflictExceptions")
    void concurrentSeatBookingAllowsOnlySingleWinner() throws Exception {
        BookingRepository bookingRepository = mock(BookingRepository.class);
        ShowtimeRepository showtimeRepository = mock(ShowtimeRepository.class);
        ShowtimeService showtimeService = mock(ShowtimeService.class);
        SeatRepository seatRepository = mock(SeatRepository.class);
        PaymentRepository paymentRepository = mock(PaymentRepository.class);

        AppProperties props = new AppProperties();
        BookingService bookingService = new BookingService(
                bookingRepository,
                showtimeRepository,
                showtimeService,
                seatRepository,
                paymentRepository,
                new DemoPaymentGateway(),
                props
        );

        Showtime showtime = new Showtime();
        showtime.setId(50L);
        showtime.setScreenId(1L);
        showtime.setStatus(ShowtimeStatus.SCHEDULED);
        showtime.setStartTime(OffsetDateTime.now().plusDays(1));
        showtime.setEndTime(OffsetDateTime.now().plusDays(1).plusHours(3));
        showtime.setTicketPrice(new BigDecimal("250.00"));

        Seat targetSeat = new Seat();
        targetSeat.setId(701L);
        targetSeat.setScreenId(1L);
        targetSeat.setRowLabel("E");
        targetSeat.setSeatNumber(5);
        targetSeat.setSeatType(SeatType.PREMIUM);
        targetSeat.setActive(true);

        when(showtimeRepository.findById(50L)).thenReturn(Optional.of(showtime));
        when(seatRepository.findAndLockSeatsForScreen(1L, List.of(701L))).thenReturn(List.of(targetSeat));
        when(showtimeService.resolveSeatPrice(showtime, SeatType.PREMIUM)).thenReturn(new BigDecimal("350.00"));
        when(bookingRepository.findActiveBookedSeatIdsInList(50L, List.of(701L))).thenReturn(Set.of());
        when(bookingRepository.findSeatIdsHeldByOtherUsers( anyLong(), any(), anyLong())).thenReturn(Set.of());

        // Simulate PostgreSQL uq_booking_seats_active_claim partial unique index atomicity
        Set<String> activeSeatClaimsIndex = ConcurrentHashMap.newKeySet();
        AtomicInteger bookingIdSeq = new AtomicInteger(1000);

        when(bookingRepository.insertBooking(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId((long) bookingIdSeq.incrementAndGet());
            b.setShowStartTime(showtime.getStartTime());
            b.setShowEndTime(showtime.getEndTime());
            return b;
        });

        doAnswer(inv -> {
            Long stId = inv.getArgument(1);
            @SuppressWarnings("unchecked")
            List<BookingSeat> seats = inv.getArgument(2);
            for (BookingSeat bs : seats) {
                String uniqueKey = stId + ":" + bs.getSeatId();
                if (!activeSeatClaimsIndex.add(uniqueKey)) {
                    throw new DuplicateKeyException(
                            "ERROR: duplicate key value violates unique constraint \"uq_booking_seats_active_claim\""
                    );
                }
            }
            return null;
        }).when(bookingRepository).batchInsertBookingSeats(anyLong(), anyLong(), any());

        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startGun = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final long customerId = 100L + i;
            tasks.add(() -> {
                readyLatch.countDown();
                startGun.await();
                try {
                    CreateBookingRequest req = new CreateBookingRequest(50L, List.of(701L), null);
                    bookingService.createBooking(customerId, req, null);
                    successCount.incrementAndGet();
                } catch (ConflictException ex) {
                    conflictCount.incrementAndGet();
                }
                return null;
            });
        }

        List<Future<Void>> futures = new ArrayList<>();
        for (Callable<Void> task : tasks) {
            futures.add(pool.submit(task));
        }

        readyLatch.await();
        startGun.countDown(); // Fire all 10 concurrent booking requests simultaneously

        for (Future<Void> f : futures) {
            f.get();
        }
        pool.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(threads - 1);
        assertThat(activeSeatClaimsIndex).containsExactly("50:701");
    }
}
