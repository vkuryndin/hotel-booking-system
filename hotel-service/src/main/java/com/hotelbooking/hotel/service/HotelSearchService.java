package com.hotelbooking.hotel.service;

import com.hotelbooking.hotel.dto.response.HotelResponse;
import com.hotelbooking.hotel.entity.Hotel;
import com.hotelbooking.hotel.entity.HotelAmenity;
import com.hotelbooking.hotel.exception.HotelNotFoundException;
import com.hotelbooking.hotel.mapper.HotelMapper;
import com.hotelbooking.hotel.repository.HotelRepository;
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
public class HotelSearchService {

  private final HotelRepository hotelRepository;
  private final HotelMapper hotelMapper;

  public HotelSearchService(HotelRepository hotelRepository, HotelMapper hotelMapper) {
    this.hotelRepository = hotelRepository;
    this.hotelMapper = hotelMapper;
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
}
