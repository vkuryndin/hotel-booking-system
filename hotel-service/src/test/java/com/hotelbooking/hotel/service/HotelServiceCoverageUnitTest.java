package com.hotelbooking.hotel.service;

import com.hotelbooking.hotel.dto.request.HotelRequest;
import com.hotelbooking.hotel.entity.Hotel;
import com.hotelbooking.hotel.entity.HotelAmenity;
import com.hotelbooking.hotel.exception.*;
import com.hotelbooking.hotel.mapper.HotelMapper;
import com.hotelbooking.hotel.repository.HotelRepository;
import com.hotelbooking.hotel.repository.RoomRepository;
import com.hotelbooking.hotel.strategy.RoomSelectionMode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HotelServiceCoverageUnitTest {

    private final HotelRepository hotels = mock(HotelRepository.class);
    private final RoomRepository rooms = mock(RoomRepository.class);
    private final HotelMapper mapper = mock(HotelMapper.class);
    private final HotelPhotoService photos = mock(HotelPhotoService.class);
    private final HotelService service = new HotelService(hotels, rooms, mapper, photos);

    @Test
    void missingHotelById() {
        assertThrows(HotelNotFoundException.class, () -> service.getHotelById(91L));
    }

    @Test
    void missingHotelStrategy() {
        assertThrows(HotelNotFoundException.class, () -> service.getRoomSelectionStrategy(91L));
    }

    @Test
    void missingHotelStrategyUpdate() {
        assertThrows(HotelNotFoundException.class,
                () -> service.updateRoomSelectionStrategy(91L, RoomSelectionMode.MOST_EXPENSIVE));
    }

    @Test
    void getStrategyFromEntity() {
        Hotel hotel = mock(Hotel.class);
        when(hotels.findById(7L)).thenReturn(Optional.of(hotel));
        when(hotel.getRoomSelectionStrategy()).thenReturn(RoomSelectionMode.LEAST_BOOKED);

        assertEquals(RoomSelectionMode.LEAST_BOOKED, service.getRoomSelectionStrategy(7L));
    }

    @Test
    void updateStrategyLocksHotel() {
        Hotel hotel = mock(Hotel.class);
        when(hotels.findByIdForUpdate(7L)).thenReturn(Optional.of(hotel));
        when(hotel.getRoomSelectionStrategy()).thenReturn(RoomSelectionMode.MOST_EXPENSIVE);

        assertEquals(RoomSelectionMode.MOST_EXPENSIVE,
                service.updateRoomSelectionStrategy(7L, RoomSelectionMode.MOST_EXPENSIVE));
        verify(hotel).changeRoomSelectionStrategy(RoomSelectionMode.MOST_EXPENSIVE);
    }

    @Test
    void createDuplicateDoesNotSave() {
        when(hotels.existsByNameIgnoreCaseAndAddressIgnoreCase("North", "Main"))
                .thenReturn(true);

        assertThrows(HotelAlreadyExistsException.class,
                () -> service.createHotel(new HotelRequest(" North ", " Main ")));
        verify(hotels, never()).saveAndFlush(any(Hotel.class));
    }

    @Test
    void createTrimsFields() {
        when(hotels.saveAndFlush(any(Hotel.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.createHotel(new HotelRequest(" North ", " Main "));

        verify(hotels).existsByNameIgnoreCaseAndAddressIgnoreCase("North", "Main");
        verify(mapper).toResponse(argThat(hotel ->
                "North".equals(hotel.getName()) && "Main".equals(hotel.getAddress())));
    }

    @Test
    void updateMissingHotel() {
        assertThrows(HotelNotFoundException.class,
                () -> service.updateHotel(9L, new HotelRequest("N", "A")));
    }

    @Test
    void deleteMissingHotel() {
        assertThrows(HotelNotFoundException.class, () -> service.deleteHotel(9L));
        verifyNoInteractions(photos);
    }

    @Test
    void deleteHotelContainingRoomsDenied() {
        Hotel hotel = mock(Hotel.class);
        when(hotels.findByIdForUpdate(9L)).thenReturn(Optional.of(hotel));
        when(rooms.existsByHotelId(9L)).thenReturn(true);

        assertThrows(ResourceInUseException.class, () -> service.deleteHotel(9L));
        verifyNoInteractions(photos);
        verify(hotels, never()).delete(any(Hotel.class));
    }

    @Test
    void deleteEmptyHotelAlsoDeletesPhotoMetadata() {
        Hotel hotel = mock(Hotel.class);
        when(hotels.findByIdForUpdate(9L)).thenReturn(Optional.of(hotel));

        service.deleteHotel(9L);

        verify(photos).removeAllForHotelDeletion(9L);
        verify(hotels).delete(hotel);
        verify(hotels).flush();
    }

    @Test
    void searchRejectsLargePage() {
        assertThrows(IllegalArgumentException.class,
                () -> search(null, "id", "asc", 101));
        verifyNoInteractions(hotels);
    }

    @Test
    void searchRejectsNegativeRating() {
        assertThrows(IllegalArgumentException.class,
                () -> search(new BigDecimal("-0.1"), "id", "asc", 10));
    }

    @Test
    void searchRejectsRatingAboveFive() {
        assertThrows(IllegalArgumentException.class,
                () -> search(new BigDecimal("5.1"), "id", "asc", 10));
    }

    @Test
    void searchRejectsUnknownSort() {
        assertThrows(IllegalArgumentException.class,
                () -> search(null, "password", "asc", 10));
    }

    @Test
    void searchRejectsUnknownDirection() {
        assertThrows(IllegalArgumentException.class,
                () -> search(null, "id", "sideways", 10));
    }

    @Test
    void searchAcceptsValidFilters() {
        when(hotels.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        assertTrue(search(new BigDecimal("3.5"), "rating", "DESC", 10).isEmpty());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(hotels).findAll(any(Specification.class), pageableCaptor.capture());

        Sort.Order ratingSort = pageableCaptor.getValue().getSort().getOrderFor("rating");
        assertNotNull(ratingSort);
        assertEquals(Sort.Direction.DESC, ratingSort.getDirection());
    }

    private Page<?> search(BigDecimal rating, String sort, String direction, int pageSize) {
        return service.searchHotels("test", "city", rating, HotelAmenity.WIFI,
                sort, direction, PageRequest.of(0, pageSize));
    }
}
