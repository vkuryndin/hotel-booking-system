package com.hotelbooking.booking.service;
import com.hotelbooking.booking.dto.request.ChangePasswordRequest;
import com.hotelbooking.booking.entity.AppUser;
import com.hotelbooking.booking.entity.Role;
import com.hotelbooking.booking.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.hotelbooking.booking.exception.UserOperationException;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class UserPasswordServiceUnitTest {
 AppUserRepository repo; PasswordEncoder encoder; UserPasswordService service; AppUser user;
 @BeforeEach void init(){repo=mock(AppUserRepository.class);encoder=mock(PasswordEncoder.class);service=new UserPasswordService(repo,encoder);user=new AppUser("x@example.com","old-hash",Role.USER);}
 @Test void nonexistentUserIsUnauthorized(){when(repo.findById(7L)).thenReturn(Optional.empty());var e=assertThrows(UserOperationException.class,()->service.changePassword(7L,new ChangePasswordRequest("old","newpassword")));assertEquals(UserOperationException.Reason.USER_NOT_AUTHENTICATED,e.reason());verifyNoInteractions(encoder);}
 @Test void incorrectCurrentPasswordIsRejected(){when(repo.findById(7L)).thenReturn(Optional.of(user));var e=assertThrows(UserOperationException.class,()->service.changePassword(7L,new ChangePasswordRequest("wrong","newpassword")));assertEquals(UserOperationException.Reason.INVALID_INPUT,e.reason());verify(repo,never()).saveAndFlush(any());}
 @Test void unchangedPasswordIsRejected(){when(repo.findById(7L)).thenReturn(Optional.of(user));when(encoder.matches("old","old-hash")).thenReturn(true);when(encoder.matches("newpassword","old-hash")).thenReturn(true);assertThrows(UserOperationException.class,()->service.changePassword(7L,new ChangePasswordRequest("old","newpassword")));verify(repo,never()).saveAndFlush(any());}
 @Test void excessiveUtf8PasswordIsRejected(){when(repo.findById(7L)).thenReturn(Optional.of(user));when(encoder.matches("old","old-hash")).thenReturn(true);var e=assertThrows(UserOperationException.class,()->service.changePassword(7L,new ChangePasswordRequest("old","é".repeat(37))));assertEquals(UserOperationException.Reason.INVALID_INPUT,e.reason());verify(repo,never()).saveAndFlush(any());}
 @Test void validPasswordChangeHashesAndSaves(){when(repo.findById(7L)).thenReturn(Optional.of(user));when(encoder.matches("old","old-hash")).thenReturn(true);when(encoder.encode("newpassword")).thenReturn("new-hash");service.changePassword(7L,new ChangePasswordRequest("old","newpassword"));assertEquals("new-hash",user.getPasswordHash());verify(repo).saveAndFlush(user);}
}
