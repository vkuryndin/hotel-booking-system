package com.hotelbooking.hotel.repository;

import com.hotelbooking.hotel.entity.Hotel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

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
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM Hotel h WHERE h.id = :id")
    Optional<Hotel> findByIdForUpdate(@Param("id") Long id);
}