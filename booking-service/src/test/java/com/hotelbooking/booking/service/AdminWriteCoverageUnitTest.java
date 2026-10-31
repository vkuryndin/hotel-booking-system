package com.hotelbooking.booking.service;
import com.hotelbooking.booking.dto.request.CreateAdminUserRequest;
import com.hotelbooking.booking.entity.*;
import com.hotelbooking.booking.exception.*;
import com.hotelbooking.booking.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AdminWriteCoverageUnitTest {
 AppUserRepository users=mock(AppUserRepository.class);
 BookingRepository bookings=mock(BookingRepository.class);
 PasswordEncoder encoder=mock(PasswordEncoder.class);
 AdminUserService service=new AdminUserService(users,encoder,bookings);
 CreateAdminUserRequest req(){return new CreateAdminUserRequest(" Foo@EXAMPLE.com ","Password123",Role.USER,"tester",null,"John","Doe");}
 @Test void duplicateUsernameRejected(){when(users.existsByUsername("tester")).thenReturn(true);assertThrows(UsernameAlreadyRegisteredException.class,()->service.createUser(req()));verifyNoInteractions(encoder);}
 @Test void duplicateEmailRejected(){when(users.existsByEmail("foo@example.com")).thenReturn(true);assertThrows(UserOperationException.class,()->service.createUser(req()));verifyNoInteractions(encoder);}
 @Test void passwordTooLongRejected(){var r=new CreateAdminUserRequest("a@example.com","x".repeat(73),Role.USER,"someuser",null,"A","B");assertThrows(UserOperationException.class,()->service.createUser(r));verifyNoInteractions(encoder);}
 @Test void concurrentUniqueViolationIsConflict(){when(encoder.encode(anyString())).thenReturn("encoded");when(users.saveAndFlush(any(AppUser.class))).thenThrow(new DataIntegrityViolationException("unique"));assertThrows(UserOperationException.class,()->service.createUser(req()));}
 @Test void cannotDeleteSelf(){assertThrows(AccessDeniedException.class,()->service.deleteUser(4L,4L));verifyNoInteractions(users,bookings);}
 @Test void missingTargetUserRejected(){when(users.lockUsersByRole(Role.ADMIN)).thenReturn(List.of());assertThrows(UserOperationException.class,()->service.deleteUser(5L,4L));}
 @Test void lastAdminCannotBeDeleted(){AppUser admin=mock(AppUser.class);when(admin.getRole()).thenReturn(Role.ADMIN);when(users.lockUsersByRole(Role.ADMIN)).thenReturn(List.of(admin));when(users.findByIdForUpdate(5L)).thenReturn(Optional.of(admin));assertThrows(UserOperationException.class,()->service.deleteUser(5L,4L));verify(users,never()).delete(any());}
 @Test void bookingHistoryPreventsDeletion(){AppUser target=mock(AppUser.class);when(target.getRole()).thenReturn(Role.USER);when(users.lockUsersByRole(Role.ADMIN)).thenReturn(List.of());when(users.findByIdForUpdate(5L)).thenReturn(Optional.of(target));when(bookings.existsByUserId(5L)).thenReturn(true);assertThrows(UserOperationException.class,()->service.deleteUser(5L,4L));verify(users,never()).delete(any());}
 @Test void deletionFlushesChanges(){AppUser target=mock(AppUser.class);when(target.getRole()).thenReturn(Role.USER);when(users.lockUsersByRole(Role.ADMIN)).thenReturn(List.of());when(users.findByIdForUpdate(5L)).thenReturn(Optional.of(target));service.deleteUser(5L,4L);verify(users).delete(target);verify(users).flush();}
}
