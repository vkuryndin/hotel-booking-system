package com.hotelbooking.hotel.service;

import com.hotelbooking.hotel.dto.request.HotelPresentationRequest;
import com.hotelbooking.hotel.dto.response.HotelResponse;
import com.hotelbooking.hotel.entity.Hotel;
import com.hotelbooking.hotel.exception.HotelNotFoundException;
import com.hotelbooking.hotel.mapper.HotelMapper;
import com.hotelbooking.hotel.repository.HotelRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HotelPresentationService {

    private final HotelRepository hotelRepository;
    private final HotelMapper hotelMapper;

    public HotelPresentationService(
            HotelRepository hotelRepository,
            HotelMapper hotelMapper
    ) {
        this.hotelRepository = hotelRepository;
        this.hotelMapper = hotelMapper;
    }

    @Transactional
    public HotelResponse update(
            Long hotelId,
            HotelPresentationRequest request
    ) {
        if (request.description() == null
                && request.rating() == null
                && request.amenities() == null
                && request.checkInTime() == null
                && request.checkOutTime() == null) {
            throw new IllegalArgumentException("No fields to update");
        }

        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new HotelNotFoundException(hotelId));

        hotel.changePresentation(
                request.description(),
                request.rating(),
                request.amenities(),
                request.checkInTime(),
                request.checkOutTime()
        );

        return hotelMapper.toResponse(hotel);
    }
}