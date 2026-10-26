package com.cinebook.screen;

import com.cinebook.exception.BadRequestException;
import com.cinebook.exception.ConflictException;
import com.cinebook.exception.ResourceNotFoundException;
import com.cinebook.screen.dto.ScreenDto;
import com.cinebook.screen.dto.ScreenRequest;
import com.cinebook.screen.dto.SeatLayoutGeneratorRequest;
import com.cinebook.seat.Seat;
import com.cinebook.seat.SeatRepository;
import com.cinebook.seat.SeatType;
import com.cinebook.seat.dto.SeatDto;
import com.cinebook.theatre.Theatre;
import com.cinebook.theatre.TheatreRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class ScreenService {

    private final ScreenRepository screenRepository;
    private final TheatreRepository theatreRepository;
    private final SeatRepository seatRepository;

    public ScreenService(
            ScreenRepository screenRepository,
            TheatreRepository theatreRepository,
            SeatRepository seatRepository
    ) {
        this.screenRepository = screenRepository;
        this.theatreRepository = theatreRepository;
        this.seatRepository = seatRepository;
    }

    public List<ScreenDto> listScreensForTheatre(Long theatreId, boolean includeInactive) {
        theatreRepository.findById(theatreId)
                .orElseThrow(() -> new ResourceNotFoundException("Theatre not found with ID: " + theatreId));
        return screenRepository.findByTheatreId(theatreId, includeInactive).stream()
                .map(ScreenDto::from)
                .toList();
    }

    public List<ScreenDto> listAllScreens() {
        return screenRepository.findAll().stream().map(ScreenDto::from).toList();
    }

    public List<SeatDto> getScreenSeats(Long screenId) {
        screenRepository.findById(screenId)
                .orElseThrow(() -> new ResourceNotFoundException("Screen not found with ID: " + screenId));
        return seatRepository.findByScreenId(screenId).stream()
                .map(SeatDto::from)
                .toList();
    }

    @Transactional
    public ScreenDto createScreen(ScreenRequest request) {
        Theatre theatre = theatreRepository.findById(request.theatreId())
                .orElseThrow(() -> new BadRequestException("Invalid theatre ID: " + request.theatreId()));
        if (!theatre.isActive()) {
            throw new BadRequestException("Cannot add a screen to an inactive theatre.");
        }

        int regRows = request.regularRows() != null ? request.regularRows() : 3;
        int premRows = request.premiumRows() != null ? request.premiumRows() : 3;
        int recRows = request.reclinerRows() != null ? request.reclinerRows() : 2;
        int seatsPerRow = request.seatsPerRow() != null ? request.seatsPerRow() : 10;
        int totalRows = regRows + premRows + recRows;
        if (totalRows < 1 || totalRows > 26) {
            throw new BadRequestException("Total rows must be between 1 and 26 (A-Z).");
        }

        int capacity = totalRows * seatsPerRow;
        boolean active = request.active() == null || request.active();
        Screen saved = screenRepository.save(request.theatreId(), request.name().trim(), capacity, active);

        List<Seat> generated = buildSeatLayout(saved.getId(), regRows, premRows, recRows, seatsPerRow);
        seatRepository.batchInsertSeats(saved.getId(), generated);

        return ScreenDto.from(saved);
    }

    @Transactional
    public List<SeatDto> regenerateSeatLayout(Long screenId, SeatLayoutGeneratorRequest request) {
        Screen screen = screenRepository.findById(screenId)
                .orElseThrow(() -> new ResourceNotFoundException("Screen not found with ID: " + screenId));

        if (seatRepository.hasBookingsForScreenSeats(screenId)) {
            throw new ConflictException("Cannot regenerate physical seat layout for a screen that already has booking records.");
        }

        int totalRows = request.regularRows() + request.premiumRows() + request.reclinerRows();
        if (totalRows < 1 || totalRows > 26) {
            throw new BadRequestException("Total rows must be between 1 and 26 (A-Z).");
        }

        int newCapacity = totalRows * request.seatsPerRow();
        seatRepository.deleteByScreenId(screenId);

        List<Seat> generated = buildSeatLayout(
                screenId,
                request.regularRows(),
                request.premiumRows(),
                request.reclinerRows(),
                request.seatsPerRow()
        );
        seatRepository.batchInsertSeats(screenId, generated);
        screenRepository.updateCapacityAndStatus(screenId, screen.getName(), newCapacity, screen.isActive());

        return seatRepository.findByScreenId(screenId).stream()
                .map(SeatDto::from)
                .toList();
    }

    private List<Seat> buildSeatLayout(Long screenId, int regRows, int premRows, int recRows, int seatsPerRow) {
        List<Seat> list = new ArrayList<>();
        int rowIndex = 0;
        for (int r = 0; r < regRows; r++, rowIndex++) {
            addSeatsForRow(list, screenId, String.valueOf((char) ('A' + rowIndex)), seatsPerRow, SeatType.REGULAR);
        }
        for (int r = 0; r < premRows; r++, rowIndex++) {
            addSeatsForRow(list, screenId, String.valueOf((char) ('A' + rowIndex)), seatsPerRow, SeatType.PREMIUM);
        }
        for (int r = 0; r < recRows; r++, rowIndex++) {
            addSeatsForRow(list, screenId, String.valueOf((char) ('A' + rowIndex)), seatsPerRow, SeatType.RECLINER);
        }
        return list;
    }

    private void addSeatsForRow(List<Seat> list, Long screenId, String rowLabel, int seatsPerRow, SeatType type) {
        for (int n = 1; n <= seatsPerRow; n++) {
            Seat s = new Seat();
            s.setScreenId(screenId);
            s.setRowLabel(rowLabel);
            s.setSeatNumber(n);
            s.setSeatType(type);
            s.setActive(true);
            list.add(s);
        }
    }
}
