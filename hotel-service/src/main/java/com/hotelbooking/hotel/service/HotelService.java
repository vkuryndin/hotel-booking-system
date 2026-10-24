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

@Service
public class HotelService {

    private static final Logger log =
            LoggerFactory.getLogger(HotelService.class);

    private final HotelRepository hotelRepository;
    private final HotelMapper hotelMapper;

    public HotelService(
            HotelRepository hotelRepository,
            HotelMapper hotelMapper
    ) {
        this.hotelRepository = hotelRepository;
        this.hotelMapper = hotelMapper;
    }

    @Transactional
    public HotelResponse createHotel(HotelRequest request) {

        Hotel hotel = new Hotel(
                request.name(),
                request.address()
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
}