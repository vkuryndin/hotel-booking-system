package com.hotelbooking.hotel.repository;

import com.hotelbooking.hotel.entity.Hotel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface HotelRepository extends
        JpaRepository<Hotel, Long>,
        JpaSpecificationExecutor<Hotel> {
    boolean existsByNameIgnoreCaseAndAddressIgnoreCase(
            String name,
            String address
    );
    boolean existsByNameIgnoreCaseAndAddressIgnoreCaseAndIdNot(
            String name,
            String address,
            Long id
    );
}