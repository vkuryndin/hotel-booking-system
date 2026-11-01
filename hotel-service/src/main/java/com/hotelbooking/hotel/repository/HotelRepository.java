package com.hotelbooking.hotel.repository;

import com.hotelbooking.hotel.entity.Hotel;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HotelRepository
    extends JpaRepository<Hotel, Long>, JpaSpecificationExecutor<Hotel> {
  boolean existsByNameIgnoreCaseAndAddressIgnoreCase(String name, String address);

  boolean existsByNameIgnoreCaseAndAddressIgnoreCaseAndIdNot(String name, String address, Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT h FROM Hotel h WHERE h.id = :id")
  Optional<Hotel> findByIdForUpdate(@Param("id") Long id);
}
