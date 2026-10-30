package com.hotelbooking.booking.service;

import com.hotelbooking.booking.dto.request.UpdateAdminUserRequest;
import com.hotelbooking.booking.entity.AppUser;
import com.hotelbooking.booking.entity.Role;
import com.hotelbooking.booking.repository.AppUserRepository;
import com.hotelbooking.booking.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.hotelbooking.booking.exception.UserOperationException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Regression tests for self-demotion, self-deletion and loss of the last ADMIN. */
class AdminProtectionTest {
    private AppUserRepository users;
    private BookingRepository bookings;
    private AdminUserService admin;
    private AdminUserUpdateService update;

    @BeforeEach
    void setUp() {
        users = mock(AppUserRepository.class);
        bookings = mock(BookingRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        admin = new AdminUserService(users, passwordEncoder, bookings);
        update = new AdminUserUpdateService(users, passwordEncoder);
    }

    private AppUser user(Role role, String label) {
        return new AppUser(label + "@example.com", "hash", role,
                label, null, "Name", "Surname");
    }

    private UpdateAdminUserRequest roleRequest(Role role) {
        return new UpdateAdminUserRequest(null, role, null, null, null, null);
    }

    private static void conflict(Runnable operation) {
        UserOperationException ex = assertThrows(UserOperationException.class, operation::run);
        assertEquals(UserOperationException.Reason.CONFLICT, ex.reason());
    }

    @Test void adminCannotDemoteSelfEvenWithMultipleAdmins() {
        AppUser target = user(Role.ADMIN, "alice");
        when(users.findById(1L)).thenReturn(Optional.of(target));
        when(users.lockUsersByRole(Role.ADMIN)).thenReturn(List.of(target, user(Role.ADMIN, "bob")));
        assertThrows(AccessDeniedException.class,
                () -> update.updateUser(1L, roleRequest(Role.USER), 1L));
        assertEquals(Role.ADMIN, target.getRole());
        verify(users, never()).saveAndFlush(any());
    }

    @Test void lastAdminCannotBeDemotedByAnotherAdmin() {
        AppUser target = user(Role.ADMIN, "alice");
        when(users.findById(1L)).thenReturn(Optional.of(target));
        when(users.lockUsersByRole(Role.ADMIN)).thenReturn(List.of(target));
        conflict(() -> update.updateUser(1L, roleRequest(Role.USER), 2L));
        assertEquals(Role.ADMIN, target.getRole());
        verify(users, never()).saveAndFlush(any());
    }

    @Test void demotionOfOtherAdminWorksWhenAnotherAdminRemains() {
        AppUser target = user(Role.ADMIN, "alice");
        when(users.findById(1L)).thenReturn(Optional.of(target));
        when(users.lockUsersByRole(Role.ADMIN)).thenReturn(List.of(target, user(Role.ADMIN, "bob")));
        when(users.saveAndFlush(target)).thenReturn(target);
        update.updateUser(1L, roleRequest(Role.USER), 2L);
        assertEquals(Role.USER, target.getRole());
        assertEquals(1L, target.getTokenVersion());
        verify(users).saveAndFlush(target);
    }

    @Test void adminCannotDeleteSelf() {
        assertThrows(AccessDeniedException.class, () -> admin.deleteUser(1L, 1L));
        verifyNoInteractions(users, bookings);
    }

    @Test void lastAdminCannotBeDeletedByAnotherAdmin() {
        AppUser target = user(Role.ADMIN, "alice");
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(target));
        when(users.lockUsersByRole(Role.ADMIN)).thenReturn(List.of(target));
        conflict(() -> admin.deleteUser(1L, 2L));
        verify(users, never()).delete(any());
    }

    @Test void otherAdminCanBeDeletedIfAtLeastOneAdminRemains() {
        AppUser target = user(Role.ADMIN, "alice");
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(target));
        when(users.lockUsersByRole(Role.ADMIN)).thenReturn(List.of(target, user(Role.ADMIN, "bob")));
        admin.deleteUser(1L, 2L);
        verify(users).delete(target);
        verify(users).flush();
    }

    @Test void bookingHistoryStillPreventsUserDeletion() {
        AppUser target = user(Role.USER, "guest");
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(target));
        when(bookings.existsByUserId(1L)).thenReturn(true);
        conflict(() -> admin.deleteUser(1L, 2L));
        verify(users, never()).delete(any());
    }
}
