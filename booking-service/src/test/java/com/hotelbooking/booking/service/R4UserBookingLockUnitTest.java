package com.hotelbooking.booking.service;

import com.hotelbooking.booking.dto.request.BookingRequest;
import com.hotelbooking.booking.entity.AppUser;
import com.hotelbooking.booking.entity.Role;
import com.hotelbooking.booking.mapper.BookingMapper;
import com.hotelbooking.booking.repository.AppUserRepository;
import com.hotelbooking.booking.repository.BookingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class R4UserBookingLockUnitTest {
    @Test void pendingCreationRejectsUserDeletedBeforeLock() {
        var bookings = mock(BookingRepository.class);
        var users = mock(AppUserRepository.class);
        when(users.findByIdForUpdate(9L)).thenReturn(Optional.empty());
        var state = new BookingSagaStateService(bookings, mock(BookingMapper.class), users);
        var request = new BookingRequest(1L, null, false,
                LocalDate.of(2055, 1, 1), LocalDate.of(2055, 1, 3), 1, null);
        assertThrows(ResponseStatusException.class,
                () -> state.createPending(9L, request, 1L, UUID.randomUUID()));
        verify(users).findByIdForUpdate(9L);
        verifyNoInteractions(bookings);
    }

    @Test void adminDeletionLocksUserBeforeCheckingBookingHistory() {
        var users = mock(AppUserRepository.class);
        var bookings = mock(BookingRepository.class);
        var target = mock(AppUser.class);
        when(target.getRole()).thenReturn(Role.USER);
        when(users.lockUsersByRole(Role.ADMIN)).thenReturn(List.of(mock(AppUser.class)));
        when(users.findByIdForUpdate(9L)).thenReturn(Optional.of(target));
        when(bookings.existsByUserId(9L)).thenReturn(true);
        var service = new AdminUserService(users, mock(PasswordEncoder.class), bookings);
        assertThrows(ResponseStatusException.class, () -> service.deleteUser(9L, 1L));
        var order = inOrder(users, bookings);
        order.verify(users).findByIdForUpdate(9L);
        order.verify(bookings).existsByUserId(9L);
        verify(users, never()).delete(any());
    }
}
