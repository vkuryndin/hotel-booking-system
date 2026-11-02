package com.hotelbooking.hotel.service;

import com.hotelbooking.hotel.dto.request.HotelRequest;
import com.hotelbooking.hotel.dto.response.HotelResponse;
import com.hotelbooking.hotel.entity.Hotel;
import com.hotelbooking.hotel.entity.HotelAmenity;
import com.hotelbooking.hotel.exception.HotelAlreadyExistsException;
import com.hotelbooking.hotel.exception.HotelNotFoundException;
import com.hotelbooking.hotel.exception.ResourceInUseException;
import com.hotelbooking.hotel.mapper.HotelMapper;
import com.hotelbooking.hotel.repository.HotelRepository;
import com.hotelbooking.hotel.repository.RoomRepository;
import com.hotelbooking.hotel.strategy.RoomSelectionMode;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HotelService {

  private static final Logger log = LoggerFactory.getLogger(HotelService.class);

  private final HotelRepository hotelRepository;
  private final HotelMapper hotelMapper;
  private final RoomRepository roomRepository;
  private final HotelPhotoService hotelPhotoService;

  public HotelService(
      HotelRepository hotelRepository,
      RoomRepository roomRepository,
      HotelMapper hotelMapper,
      HotelPhotoService hotelPhotoService) {
    this.hotelRepository = hotelRepository;
    this.roomRepository = roomRepository;
    this.hotelMapper = hotelMapper;
    this.hotelPhotoService = hotelPhotoService;
  }

  @Transactional(readOnly = true)
  public RoomSelectionMode getRoomSelectionStrategy(Long hotelId) {
    return hotelRepository
        .findById(hotelId)
        .orElseThrow(() -> new HotelNotFoundException(hotelId))
        .getRoomSelectionStrategy();
  }

  @Transactional
  public RoomSelectionMode updateRoomSelectionStrategy(Long hotelId, RoomSelectionMode strategy) {
    Hotel hotel =
        hotelRepository
            .findByIdForUpdate(hotelId)
            .orElseThrow(() -> new HotelNotFoundException(hotelId));
    hotel.changeRoomSelectionStrategy(strategy);
    return hotel.getRoomSelectionStrategy();
  }

  @Transactional
  public HotelResponse createHotel(HotelRequest request) {

    Hotel hotel = new Hotel(request.name().trim(), request.address().trim());
    if (hotelRepository.existsByNameIgnoreCaseAndAddressIgnoreCase(
        request.name().trim(), request.address().trim())) {

      throw new HotelAlreadyExistsException(request.name(), request.address());
    }
    Hotel savedHotel;

    try {
      savedHotel = hotelRepository.saveAndFlush(hotel);
    } catch (DataIntegrityViolationException ex) {
      if (isHotelUniquenessViolation(ex)) {
        throw new HotelAlreadyExistsException(request.name(), request.address());
      }
      throw ex;
    }

    log.info("Hotel created: id={}, name={}", savedHotel.getId(), savedHotel.getName());

    return hotelMapper.toResponse(savedHotel);
  }

  @Transactional(readOnly = true)
  public Page<HotelResponse> searchHotels(
      String name,
      String address,
      BigDecimal minRating,
      HotelAmenity amenity,
      String sortBy,
      String direction,
      Pageable pageable) {
    return searchHotels(name, address, minRating, amenity, sortBy, direction, pageable, null, null);
  }

  @Transactional(readOnly = true)
  public Page<HotelResponse> searchHotels(
      String name,
      String address,
      BigDecimal minRating,
      HotelAmenity amenity,
      String sortBy,
      String direction,
      Pageable pageable,
      String textQuery,
      List<HotelAmenity> selectedAmenities) {
    if (pageable.getPageSize() > 100) {
      throw new IllegalArgumentException("Page size must not exceed 100");
    }
    if (minRating != null
        && (minRating.compareTo(BigDecimal.ZERO) < 0
            || minRating.compareTo(BigDecimal.valueOf(5)) > 0)) {
      throw new IllegalArgumentException("minRating must be between 0 and 5");
    }

    String sortProperty =
        switch (sortBy) {
          case "id", "name", "address", "rating" -> sortBy;
          default -> throw new IllegalArgumentException("Unsupported hotel sortBy: " + sortBy);
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

    Specification<Hotel> specification =
        (root, query, cb) -> {
          List<Predicate> predicates = new ArrayList<>();

          if (name != null && !name.isBlank()) {
            predicates.add(
                cb.like(
                    cb.lower(root.get("name")), "%" + name.trim().toLowerCase(Locale.ROOT) + "%"));
          }

          if (address != null && !address.isBlank()) {
            predicates.add(
                cb.like(
                    cb.lower(root.get("address")),
                    "%" + address.trim().toLowerCase(Locale.ROOT) + "%"));
          }

          if (textQuery != null && !textQuery.isBlank()) {
            String escaped =
                textQuery
                    .trim()
                    .toLowerCase(Locale.ROOT)
                    .replace("!", "!!")
                    .replace("%", "!%")
                    .replace("_", "!_");
            String needle = "%" + escaped + "%";
            predicates.add(
                cb.or(
                    cb.like(cb.lower(root.get("name")), needle, '!'),
                    cb.like(cb.lower(root.get("address")), needle, '!')));
          }

          if (minRating != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("rating"), minRating));
          }

          if (amenity != null) {
            predicates.add(cb.isMember(amenity, root.<Collection<HotelAmenity>>get("amenities")));
          }

          if (selectedAmenities != null) {
            for (HotelAmenity selected : selectedAmenities) {
              predicates.add(
                  cb.isMember(selected, root.<Collection<HotelAmenity>>get("amenities")));
            }
          }

          return cb.and(predicates.toArray(new Predicate[0]));
        };

    Pageable pageRequest = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);

    return hotelRepository.findAll(specification, pageRequest).map(hotelMapper::toResponse);
  }

  @Transactional(readOnly = true)
  public HotelResponse getHotelById(Long id) {
    Hotel hotel = hotelRepository.findById(id).orElseThrow(() -> new HotelNotFoundException(id));

    return hotelMapper.toResponse(hotel);
  }

  @Transactional
  public HotelResponse updateHotel(Long id, HotelRequest request) {
    Hotel hotel = hotelRepository.findById(id).orElseThrow(() -> new HotelNotFoundException(id));

    String name = request.name().trim();
    String address = request.address().trim();

    hotel.updateDetails(name, address);

    try {
      hotelRepository.saveAndFlush(hotel);
    } catch (DataIntegrityViolationException ex) {
      if (isHotelUniquenessViolation(ex)) {
        throw new HotelAlreadyExistsException(name, address);
      }
      throw ex;
    }

    log.info("Hotel updated: id={}", id);

    return hotelMapper.toResponse(hotel);
  }

  @Transactional
  public void deleteHotel(Long id) {
    // Share the photo-service lock to prevent deletion during concurrent photo updates.
    Hotel hotel =
        hotelRepository.findByIdForUpdate(id).orElseThrow(() -> new HotelNotFoundException(id));

    if (roomRepository.existsByHotelId(id)) {
      throw new ResourceInUseException(
          "HOTEL_HAS_ROOMS", "Cannot delete hotel with existing rooms: " + id);
    }

    // Keep photo metadata deletion in this transaction; delete files only after commit.
    hotelPhotoService.removeAllForHotelDeletion(id);

    hotelRepository.delete(hotel);
    hotelRepository.flush();

    log.info("Hotel deleted: id={}", id);
  }

  private boolean isHotelUniquenessViolation(DataIntegrityViolationException ex) {
    for (Throwable cause = ex; cause != null; cause = cause.getCause()) {

      if (cause instanceof org.hibernate.exception.ConstraintViolationException c) {

        String name = c.getConstraintName();

        if (name != null) {
          String normalized = name.toLowerCase(Locale.ROOT);

          if (normalized.contains("uk_hotel_name_address")
              || normalized.contains("uk_hotel_normalized_name_address")) {
            return true;
          }
        }
      }
    }

    return false;
  }
}
