package com.hotelbooking.hotel.repository;

import com.hotelbooking.hotel.entity.RoomPhoto;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomPhotoRepository extends JpaRepository<RoomPhoto, Long> {

  List<RoomPhoto> findByRoom_IdOrderBySortOrderAscIdAsc(Long roomId);

  long countByRoom_Id(Long roomId);
}
