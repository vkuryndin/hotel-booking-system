package com.hotelbooking.hotel.service;

import com.hotelbooking.hotel.dto.request.RoomAvailabilityRequest;
import com.hotelbooking.hotel.dto.request.RoomRequest;
import com.hotelbooking.hotel.dto.response.RoomResponse;
import com.hotelbooking.hotel.entity.Hotel;
import com.hotelbooking.hotel.entity.Room;
import com.hotelbooking.hotel.exception.HotelNotFoundException;
import com.hotelbooking.hotel.exception.RoomAlreadyExistsException;
import com.hotelbooking.hotel.exception.RoomNotFoundException;
import com.hotelbooking.hotel.mapper.RoomMapper;
import com.hotelbooking.hotel.repository.HotelRepository;
import com.hotelbooking.hotel.repository.RoomRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.hotelbooking.hotel.strategy.RoomSelectionStrategy;
import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import java.time.temporal.ChronoUnit;
import java.util.List;
import com.hotelbooking.hotel.dto.request.RoomUpdateRequest;
import com.hotelbooking.hotel.exception.ResourceInUseException;
import com.hotelbooking.hotel.repository.RoomReservationRepository;
import com.hotelbooking.hotel.strategy.MostExpensiveStrategy;
import com.hotelbooking.hotel.strategy.RoomSelectionMode;
import org.springframework.beans.factory.annotation.Qualifier;
import com.hotelbooking.hotel.entity.RoomAmenity;
import com.hotelbooking.hotel.entity.RoomCategory;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;
@Service
public class RoomService {

    private final RoomRepository roomRepository;
    private final HotelRepository hotelRepository;
    private final RoomMapper roomMapper;
    private final RoomReservationRepository reservationRepository;
    private final RoomSelectionStrategy selectionStrategy;
    private final MostExpensiveStrategy mostExpensiveStrategy;

    public RoomService(
            RoomRepository roomRepository,
            HotelRepository hotelRepository,
            RoomMapper roomMapper,
            RoomReservationRepository reservationRepository,
            @Qualifier("leastBookedStrategy")
            RoomSelectionStrategy selectionStrategy,
            MostExpensiveStrategy mostExpensiveStrategy
    ) {
        this.roomRepository = roomRepository;
        this.hotelRepository = hotelRepository;
        this.roomMapper = roomMapper;
        this.reservationRepository = reservationRepository;
        this.selectionStrategy = selectionStrategy;
        this.mostExpensiveStrategy = mostExpensiveStrategy;
    }

    @Transactional
    public RoomResponse createRoom(RoomRequest request) {

        Hotel hotel = hotelRepository.findById(request.hotelId())
                .orElseThrow(() ->
                        new HotelNotFoundException(request.hotelId()));

        String number = request.number().trim();

        Room room = new Room(hotel, number);
        room.changeRoomDetails(
                request.category(),
                request.capacity(),
                request.pricePerNight(),
                request.amenities()
        );

        try {
            Room savedRoom = roomRepository.saveAndFlush(room);
            return roomMapper.toResponse(savedRoom);
        } catch (DataIntegrityViolationException ex) {
            throw new RoomAlreadyExistsException(
                    request.hotelId(), number);
        }
    }
    @Transactional(readOnly = true)
    public Page<RoomResponse> getRoomsByHotelId(
            Long hotelId,
            Pageable pageable
    ) {
        return searchRooms(
                hotelId, null, null, null, null,
                null, null, "id", "asc", pageable
        );
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
            Pageable pageable
    ) {
        if (pageable.getPageSize() > 100) {
            throw new IllegalArgumentException(
                    "Page size must not exceed 100"
            );
        }
        if (hotelId == null || hotelId <= 0) {
            throw new IllegalArgumentException("Invalid hotelId");
        }

        if (!hotelRepository.existsById(hotelId)) {
            throw new HotelNotFoundException(hotelId);
        }

        if (minCapacity != null && minCapacity < 1) {
            throw new IllegalArgumentException(
                    "minCapacity must be positive"
            );
        }

        if ((minPrice != null && minPrice.signum() < 0)
                || (maxPrice != null && maxPrice.signum() < 0)
                || (minPrice != null && maxPrice != null
                && minPrice.compareTo(maxPrice) > 0)) {
            throw new IllegalArgumentException(
                    "Invalid price range"
            );
        }

        String sortProperty = switch (sortBy) {
            case "id", "number", "capacity",
                 "pricePerNight", "timesBooked" -> sortBy;

            default -> throw new IllegalArgumentException(
                    "Unsupported room sortBy: " + sortBy
            );
        };

        String order = direction.toLowerCase(Locale.ROOT);

        if (!order.equals("asc") && !order.equals("desc")) {
            throw new IllegalArgumentException(
                    "direction must be asc or desc"
            );
        }

        Sort.Direction sortDirection = order.equals("desc")
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;

        Sort sort = Sort.by(sortDirection, sortProperty);

        if (!sortProperty.equals("id")) {
            sort = sort.and(Sort.by("id"));
        }

        Specification<Room> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(
                    root.get("hotel").get("id"),
                    hotelId
            ));

            if (category != null) {
                predicates.add(cb.equal(
                        root.get("category"),
                        category
                ));
            }

            if (minCapacity != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.get("capacity"),
                        minCapacity
                ));
            }

            if (minPrice != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.get("pricePerNight"),
                        minPrice
                ));
            }

            if (maxPrice != null) {
                predicates.add(cb.lessThanOrEqualTo(
                        root.get("pricePerNight"),
                        maxPrice
                ));
            }

            if (available != null) {
                predicates.add(cb.equal(
                        root.get("available"),
                        available
                ));
            }

            if (amenity != null) {
                predicates.add(cb.isMember(
                        amenity,
                        root.<Collection<RoomAmenity>>get("amenities")
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Pageable pageRequest = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                sort
        );

        return roomRepository.findAll(specification, pageRequest)
                .map(roomMapper::toResponse);
    }
    @Transactional(readOnly = true)
    public RoomResponse getRoomById(Long roomId) {

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException(roomId));

        return roomMapper.toResponse(room);
    }
    @Transactional
    public RoomResponse updateAvailability(
            Long roomId,
            RoomAvailabilityRequest request
    ) {
        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new RoomNotFoundException(roomId));

        room.setAvailable(request.available());

        return roomMapper.toResponse(room);
    }

    @Transactional(readOnly = true)
    public Page<RoomResponse> getAvailableRoomsByHotelId(
            Long hotelId,
            Pageable pageable
    ) {
        return searchRooms(
                hotelId, null, null, null, null,
                true, null, "id", "asc", pageable
        );
    }

    @Transactional
    public RoomResponse updateRoom(
            Long roomId,
            RoomUpdateRequest request
    ) {
        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new RoomNotFoundException(roomId));

        String number = request.number().trim();
        Long hotelId = room.getHotel().getId();

        if (roomRepository.existsByHotelIdAndNumberAndIdNot(
                hotelId, number, roomId)) {
            throw new RoomAlreadyExistsException(hotelId, number);
        }

        room.changeNumber(number);

        try {
            roomRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new RoomAlreadyExistsException(hotelId, number);
        }

        return roomMapper.toResponse(room);
    }

    @Transactional
    public void deleteRoom(Long roomId) {
        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new RoomNotFoundException(roomId));

        if (reservationRepository.existsByRoom_Id(roomId)) {
            throw new ResourceInUseException(
                    "ROOM_HAS_RESERVATIONS",
                    "Cannot delete room with reservation history: " + roomId
            );
        }

        try {
            roomRepository.delete(room);
            roomRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new ResourceInUseException(
                    "ROOM_HAS_RESERVATIONS",
                    "Cannot delete room with reservation history: " + roomId
            );
        }
    }
    @Transactional(readOnly = true)
    public List<RoomResponse> recommendRooms(
            Long hotelId,
            LocalDate checkIn,
            LocalDate checkOut
    ) {
        return recommendRooms(
                hotelId,
                checkIn,
                checkOut,
                RoomSelectionMode.LEAST_BOOKED,
                1
        );
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> recommendRooms(
            Long hotelId,
            LocalDate checkIn,
            LocalDate checkOut,
            RoomSelectionMode strategy,
            int guestCount
    ) {
        if (hotelId == null || hotelId <= 0) {
            throw new IllegalArgumentException("Invalid hotelId");
        }

        if (checkIn == null
                || checkOut == null
                || checkIn.isBefore(LocalDate.now())
                || !checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException("Invalid booking dates");
        }
        if (ChronoUnit.DAYS.between(checkIn, checkOut) > 365) {
            throw new IllegalArgumentException(
                    "Booking cannot exceed 365 nights"
            );
        }

        if (guestCount < 1) {
            throw new IllegalArgumentException(
                    "guestCount must be positive"
            );
        }

        if (!hotelRepository.existsById(hotelId)) {
            throw new HotelNotFoundException(hotelId);
        }

        List<Room> available = roomRepository
                .findAvailableForPeriod(hotelId, checkIn, checkOut)
                .stream()
                .filter(room -> room.getCapacity() >= guestCount)
                .toList();

        RoomSelectionMode mode = strategy == null
                ? RoomSelectionMode.LEAST_BOOKED
                : strategy;

        List<Room> ranked = mode == RoomSelectionMode.MOST_EXPENSIVE
                ? mostExpensiveStrategy.rank(available)
                : selectionStrategy.rank(available);

        return ranked.stream()
                .map(roomMapper::toResponse)
                .toList();
    }
}