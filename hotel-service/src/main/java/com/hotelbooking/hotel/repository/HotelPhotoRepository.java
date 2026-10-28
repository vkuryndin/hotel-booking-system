package com.hotelbooking.hotel.repository;

import com.hotelbooking.hotel.entity.HotelPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HotelPhotoRepository
        extends JpaRepository<HotelPhoto, Long> {

    List<HotelPhoto> findByHotel_IdOrderBySortOrderAscIdAsc(
            Long hotelId
    );

    long countByHotel_Id(Long hotelId);
}