package com.hotelbooking.hotel.service;

import com.hotelbooking.hotel.dto.request.RoomRequest;
import com.hotelbooking.hotel.entity.Hotel;
import com.hotelbooking.hotel.entity.Room;
import com.hotelbooking.hotel.exception.*;
import com.hotelbooking.hotel.mapper.RoomMapper;
import com.hotelbooking.hotel.repository.HotelRepository;
import com.hotelbooking.hotel.repository.RoomRepository;
import com.hotelbooking.hotel.repository.RoomReservationRepository;
import com.hotelbooking.hotel.strategy.MostExpensiveStrategy;
import com.hotelbooking.hotel.strategy.RoomSelectionStrategy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoomServiceCoverageUnitTest {

    private final RoomRepository rooms = mock(RoomRepository.class);
    private final HotelRepository hotels = mock(HotelRepository.class);
    private final RoomMapper mapper = mock(RoomMapper.class);
    private final RoomReservationRepository reservations = mock(RoomReservationRepository.class);
    private final RoomSelectionStrategy least = mock(RoomSelectionStrategy.class);
    private final MostExpensiveStrategy expensive = mock(MostExpensiveStrategy.class);
    private final RoomPhotoService photos = mock(RoomPhotoService.class);
    private final RoomService service = new RoomService(
            rooms, hotels, mapper, reservations, least, expensive, photos);

    @Test
    void createRoomRequiresExistingHotel() {
        assertThrows(HotelNotFoundException.class,
                () -> service.createRoom(new RoomRequest(
                        19L, "1", null, 2, BigDecimal.TEN, Set.of())));
        verifyNoInteractions(rooms);
    }

    @Test
    void createRoomConvertsUniqueConstraint() {
        Hotel hotel = mock(Hotel.class);
        when(hotels.findByIdForUpdate(1L)).thenReturn(Optional.of(hotel));
        when(rooms.saveAndFlush(any(Room.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThrows(RoomAlreadyExistsException.class,
                () -> service.createRoom(new RoomRequest(
                        1L, " 5 ", null, 2, BigDecimal.TEN, Set.of())));
    }

    @Test
    void missingRoomGet() {
        assertThrows(RoomNotFoundException.class, () -> service.getRoomById(23L));
    }

    @Test
    void missingRoomDelete() {
        assertThrows(RoomNotFoundException.class, () -> service.deleteRoom(23L));
        verifyNoInteractions(photos);
    }

    @Test
    void roomWithReservationCannotBeDeleted() {
        Room room = mock(Room.class);
        when(rooms.findByIdForUpdate(23L)).thenReturn(Optional.of(room));
        when(reservations.existsByRoom_Id(23L)).thenReturn(true);

        assertThrows(ResourceInUseException.class, () -> service.deleteRoom(23L));
        verifyNoInteractions(photos);
    }

    @Test
    void deleteRoomRemovesPhotosThenRow() {
        Room room = mock(Room.class);
        when(rooms.findByIdForUpdate(23L)).thenReturn(Optional.of(room));

        service.deleteRoom(23L);

        var order = inOrder(photos, rooms);
        order.verify(photos).removeAllForRoomDeletion(23L);
        order.verify(rooms).delete(room);
        order.verify(rooms).flush();
    }

    @Test
    void deleteDatabaseConflictIsResourceInUse() {
        Room room = mock(Room.class);
        when(rooms.findByIdForUpdate(23L)).thenReturn(Optional.of(room));
        doThrow(new DataIntegrityViolationException("fk")).when(rooms).flush();

        assertThrows(ResourceInUseException.class, () -> service.deleteRoom(23L));
    }

    @Test
    void roomSearchRejectsHugePage() {
        assertThrows(IllegalArgumentException.class,
                () -> search(1L, null, null, null, "id", "asc", 101));
    }

    @Test
    void roomSearchRequiresHotelId() {
        assertThrows(IllegalArgumentException.class,
                () -> search(null, null, null, null, "id", "asc", 10));
    }

    @Test
    void roomSearchRequiresExistingHotel() {
        assertThrows(HotelNotFoundException.class,
                () -> search(1L, null, null, null, "id", "asc", 10));
    }

    @Test
    void roomSearchRejectsInvalidCapacity() {
        when(hotels.existsById(1L)).thenReturn(true);
        assertThrows(IllegalArgumentException.class,
                () -> search(1L, 0, null, null, "id", "asc", 10));
    }

    @Test
    void roomSearchRejectsNegativeMinPrice() {
        when(hotels.existsById(1L)).thenReturn(true);
        assertThrows(IllegalArgumentException.class,
                () -> search(1L, null, BigDecimal.ONE.negate(), null, "id", "asc", 10));
    }

    @Test
    void roomSearchRejectsNegativeMaxPrice() {
        when(hotels.existsById(1L)).thenReturn(true);
        assertThrows(IllegalArgumentException.class,
                () -> search(1L, null, null, BigDecimal.ONE.negate(), "id", "asc", 10));
    }

    @Test
    void roomSearchRejectsInvertedPriceRange() {
        when(hotels.existsById(1L)).thenReturn(true);
        assertThrows(IllegalArgumentException.class,
                () -> search(1L, null, BigDecimal.TEN, BigDecimal.ONE, "id", "asc", 10));
    }

    @Test
    void roomSearchRejectsUnknownSort() {
        when(hotels.existsById(1L)).thenReturn(true);
        assertThrows(IllegalArgumentException.class,
                () -> search(1L, null, null, null, "hotel.password", "asc", 10));
    }

    @Test
    void roomSearchRejectsBadDirection() {
        when(hotels.existsById(1L)).thenReturn(true);
        assertThrows(IllegalArgumentException.class,
                () -> search(1L, null, null, null, "id", "down", 10));
    }

    @Test
    void roomSearchUsesPaginationAndSort() {
        when(hotels.existsById(1L)).thenReturn(true);
        when(rooms.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        assertTrue(search(1L, 2, null, null, "number", "DESC", 10).isEmpty());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(rooms).findAll(any(Specification.class), pageableCaptor.capture());

        Sort.Order numberSort = pageableCaptor.getValue().getSort().getOrderFor("number");
        assertNotNull(numberSort);
        assertEquals(Sort.Direction.DESC, numberSort.getDirection());
    }

    @Test
    void recommendationRejectsInvalidHotel() {
        assertThrows(IllegalArgumentException.class,
                () -> service.recommendRooms(0L,
                        LocalDate.now().plusDays(1), LocalDate.now().plusDays(2), null, 1));
    }

    @Test
    void recommendationRejectsPastDate() {
        assertThrows(IllegalArgumentException.class,
                () -> service.recommendRooms(1L,
                        LocalDate.now().minusDays(1), LocalDate.now().plusDays(2), null, 1));
    }

    @Test
    void recommendationRejectsLongStay() {
        assertThrows(IllegalArgumentException.class,
                () -> service.recommendRooms(1L,
                        LocalDate.now().plusDays(1), LocalDate.now().plusDays(367), null, 1));
    }

    @Test
    void recommendationRejectsZeroGuests() {
        assertThrows(IllegalArgumentException.class,
                () -> service.recommendRooms(1L,
                        LocalDate.now().plusDays(1), LocalDate.now().plusDays(2), null, 0));
    }

    private Page<?> search(Long hotelId, Integer capacity, BigDecimal minPrice,
                           BigDecimal maxPrice, String sort, String direction, int pageSize) {
        return service.searchRooms(hotelId, null, capacity, minPrice, maxPrice,
                null, null, sort, direction, PageRequest.of(0, pageSize));
    }
}
