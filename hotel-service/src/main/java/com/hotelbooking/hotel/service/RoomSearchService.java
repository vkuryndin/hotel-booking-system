package com.hotelbooking.hotel.service;

import com.hotelbooking.hotel.dto.response.RoomResponse;
import com.hotelbooking.hotel.entity.Room;
import com.hotelbooking.hotel.entity.RoomAmenity;
import com.hotelbooking.hotel.entity.RoomCategory;
import com.hotelbooking.hotel.exception.HotelNotFoundException;
import com.hotelbooking.hotel.exception.RoomNotFoundException;
import com.hotelbooking.hotel.mapper.RoomMapper;
import com.hotelbooking.hotel.repository.HotelRepository;
import com.hotelbooking.hotel.repository.RoomRepository;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomSearchService {

  private final RoomRepository roomRepository;
  private final HotelRepository hotelRepository;
  private final RoomMapper roomMapper;

  public RoomSearchService(
      RoomRepository roomRepository, HotelRepository hotelRepository, RoomMapper roomMapper) {
    this.roomRepository = roomRepository;
    this.hotelRepository = hotelRepository;
    this.roomMapper = roomMapper;
  }

  @Transactional(readOnly = true)
  public Page<RoomResponse> getRoomsByHotelId(Long hotelId, Pageable pageable) {
    return searchRooms(hotelId, null, null, null, null, null, null, "id", "asc", pageable);
  }

  @Transactional(readOnly = true)
  public Page<RoomResponse> searchRooms(
      Long hotelId,
      RoomCategory category,
      Integer minCapacity,
      BigDecimal minPrice,
      BigDecimal maxPrice,
      Boolean available,
      RoomAmenity amenity,
      String sortBy,
      String direction,
      Pageable pageable) {
    return searchRooms(
        new RoomSearchCriteria(
            hotelId,
            category,
            minCapacity,
            minPrice,
            maxPrice,
            available,
            amenity,
            sortBy,
            direction,
            pageable));
  }

  private Page<RoomResponse> searchRooms(RoomSearchCriteria criteria) {
    Long hotelId = criteria.hotelId();
    final RoomCategory category = criteria.category();
    final Integer minCapacity = criteria.minCapacity();
    final BigDecimal minPrice = criteria.minPrice();
    final BigDecimal maxPrice = criteria.maxPrice();
    final Boolean available = criteria.available();
    final RoomAmenity amenity = criteria.amenity();
    final String sortBy = criteria.sortBy();
    final String direction = criteria.direction();
    Pageable pageable = criteria.pageable();

    if (pageable.getPageSize() > 100) {
      throw new IllegalArgumentException("Page size must not exceed 100");
    }
    if (hotelId == null || hotelId <= 0) {
      throw new IllegalArgumentException("Invalid hotelId");
    }

    if (!hotelRepository.existsById(hotelId)) {
      throw new HotelNotFoundException(hotelId);
    }

    if (minCapacity != null && minCapacity < 1) {
      throw new IllegalArgumentException("minCapacity must be positive");
    }

    if ((minPrice != null && minPrice.signum() < 0)
        || (maxPrice != null && maxPrice.signum() < 0)
        || (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0)) {
      throw new IllegalArgumentException("Invalid price range");
    }

    String sortProperty =
        switch (sortBy) {
          case "id", "number", "capacity", "pricePerNight", "timesBooked" -> sortBy;

          default -> throw new IllegalArgumentException("Unsupported room sortBy: " + sortBy);
        };

    String order = direction.toLowerCase(Locale.ROOT);

    if (!order.equals("asc") && !order.equals("desc")) {
      throw new IllegalArgumentException("direction must be asc or desc");
    }

    Sort.Direction sortDirection = order.equals("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;

    Sort sort = Sort.by(sortDirection, sortProperty);

    if (!sortProperty.equals("id")) {
      sort = sort.and(Sort.by("id"));
    }

    Specification<Room> specification =
        (root, query, cb) -> {
          List<Predicate> predicates = new ArrayList<>();

          predicates.add(cb.equal(root.get("hotel").get("id"), hotelId));

          if (category != null) {
            predicates.add(cb.equal(root.get("category"), category));
          }

          if (minCapacity != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("capacity"), minCapacity));
          }

          if (minPrice != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("pricePerNight"), minPrice));
          }

          if (maxPrice != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("pricePerNight"), maxPrice));
          }

          if (available != null) {
            predicates.add(cb.equal(root.get("available"), available));
          }

          if (amenity != null) {
            predicates.add(cb.isMember(amenity, root.<Collection<RoomAmenity>>get("amenities")));
          }

          return cb.and(predicates.toArray(new Predicate[0]));
        };

    Pageable pageRequest = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);

    return roomRepository.findAll(specification, pageRequest).map(roomMapper::toResponse);
  }

  private record RoomSearchCriteria(
      Long hotelId,
      RoomCategory category,
      Integer minCapacity,
      BigDecimal minPrice,
      BigDecimal maxPrice,
      Boolean available,
      RoomAmenity amenity,
      String sortBy,
      String direction,
      Pageable pageable) {}

  @Transactional(readOnly = true)
  public RoomResponse getRoomById(Long roomId) {

    Room room =
        roomRepository.findById(roomId).orElseThrow(() -> new RoomNotFoundException(roomId));

    return roomMapper.toResponse(room);
  }

  @Transactional(readOnly = true)
  public Page<RoomResponse> getAvailableRoomsByHotelId(Long hotelId, Pageable pageable) {
    return searchRooms(hotelId, null, null, null, null, true, null, "id", "asc", pageable);
  }
}
