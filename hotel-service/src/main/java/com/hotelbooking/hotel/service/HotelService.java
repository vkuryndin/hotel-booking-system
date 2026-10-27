package com.hotelbooking.hotel.service;

import com.hotelbooking.hotel.dto.request.HotelRequest;
import com.hotelbooking.hotel.dto.response.HotelResponse;
import com.hotelbooking.hotel.entity.Hotel;
import com.hotelbooking.hotel.exception.HotelAlreadyExistsException;
import com.hotelbooking.hotel.mapper.HotelMapper;
import com.hotelbooking.hotel.repository.HotelRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.hotelbooking.hotel.exception.HotelNotFoundException;
import com.hotelbooking.hotel.exception.ResourceInUseException;
import com.hotelbooking.hotel.repository.RoomRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;

import java.util.List;

@Service
public class HotelService {

    private static final Logger log =
            LoggerFactory.getLogger(HotelService.class);

    private final HotelRepository hotelRepository;
    private final HotelMapper hotelMapper;
    private final RoomRepository roomRepository;

    public HotelService(
            HotelRepository hotelRepository,
            RoomRepository roomRepository,
            HotelMapper hotelMapper
    ) {
        this.hotelRepository = hotelRepository;
        this.roomRepository = roomRepository;
        this.hotelMapper = hotelMapper;
    }

    @Transactional
    public HotelResponse createHotel(HotelRequest request) {

        Hotel hotel = new Hotel(
                request.name().trim(),
                request.address().trim()
        );
        if (hotelRepository.existsByNameIgnoreCaseAndAddressIgnoreCase(
                request.name().trim(),
                request.address().trim())) {

            throw new HotelAlreadyExistsException(
                    request.name(),
                    request.address()
            );
        }
        Hotel savedHotel = hotelRepository.save(hotel);

        log.info("Hotel created: id={}, name={}",
                savedHotel.getId(),
                savedHotel.getName());

        return hotelMapper.toResponse(savedHotel);
    }
    @Transactional(readOnly = true)
    public List<HotelResponse> getHotels() {
        return hotelRepository.findAll(
                        Sort.by(Sort.Direction.ASC, "id"))
                .stream()
                .map(hotelMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public HotelResponse getHotelById(Long id) {
        Hotel hotel = hotelRepository.findById(id)
                .orElseThrow(() -> new HotelNotFoundException(id));

        return hotelMapper.toResponse(hotel);
    }

    @Transactional
    public HotelResponse updateHotel(Long id, HotelRequest request) {
        Hotel hotel = hotelRepository.findById(id)
                .orElseThrow(() -> new HotelNotFoundException(id));

        String name = request.name().trim();
        String address = request.address().trim();

        if (hotelRepository
                .existsByNameIgnoreCaseAndAddressIgnoreCaseAndIdNot(
                        name, address, id)) {
            throw new HotelAlreadyExistsException(name, address);
        }

        hotel.updateDetails(name, address);

        try {
            hotelRepository.saveAndFlush(hotel);
        } catch (DataIntegrityViolationException ex) {
            throw new HotelAlreadyExistsException(name, address);
        }

        log.info("Hotel updated: id={}", id);

        return hotelMapper.toResponse(hotel);
    }

    @Transactional
    public void deleteHotel(Long id) {
        Hotel hotel = hotelRepository.findById(id)
                .orElseThrow(() -> new HotelNotFoundException(id));

        if (roomRepository.existsByHotelId(id)) {
            throw new ResourceInUseException(
                    "HOTEL_HAS_ROOMS",
                    "Cannot delete hotel with existing rooms: " + id
            );
        }

        try {
            hotelRepository.delete(hotel);
            hotelRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new ResourceInUseException(
                    "HOTEL_HAS_ROOMS",
                    "Cannot delete hotel with existing rooms: " + id
            );
        }

        log.info("Hotel deleted: id={}", id);
    }
}