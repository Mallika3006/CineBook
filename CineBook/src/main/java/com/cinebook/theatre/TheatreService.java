package com.cinebook.theatre;

import com.cinebook.exception.ResourceNotFoundException;
import com.cinebook.theatre.dto.TheatreDto;
import com.cinebook.theatre.dto.TheatreRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TheatreService {

    private final TheatreRepository theatreRepository;

    public TheatreService(TheatreRepository theatreRepository) {
        this.theatreRepository = theatreRepository;
    }

    public List<TheatreDto> listTheatres(String city, boolean includeInactive) {
        return theatreRepository.findAll(city, includeInactive).stream()
                .map(TheatreDto::from)
                .toList();
    }

    public TheatreDto getTheatre(Long id) {
        Theatre t = theatreRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Theatre not found with ID: " + id));
        return TheatreDto.from(t);
    }

    @Transactional
    public TheatreDto createTheatre(TheatreRequest request) {
        Theatre t = new Theatre();
        t.setName(request.name().trim());
        t.setAddress(request.address().trim());
        t.setCity(request.city().trim());
        t.setActive(request.active() == null || request.active());
        return TheatreDto.from(theatreRepository.save(t));
    }

    @Transactional
    public TheatreDto updateTheatre(Long id, TheatreRequest request) {
        theatreRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Theatre not found with ID: " + id));
        Theatre t = new Theatre();
        t.setName(request.name().trim());
        t.setAddress(request.address().trim());
        t.setCity(request.city().trim());
        t.setActive(request.active() == null || request.active());
        return TheatreDto.from(theatreRepository.update(id, t));
    }
}
