package com.hotelbooking.hotel.repository;

import com.hotelbooking.hotel.entity.HotelPhoto;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HotelPhotoRepository extends JpaRepository<HotelPhoto, Long> {

  List<HotelPhoto> findByHotel_IdOrderBySortOrderAscIdAsc(Long hotelId);

  long countByHotel_Id(Long hotelId);
}
