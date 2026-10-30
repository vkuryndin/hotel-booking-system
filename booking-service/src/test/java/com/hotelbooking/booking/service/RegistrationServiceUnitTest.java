package com.hotelbooking.booking.service;
import com.hotelbooking.booking.dto.request.RegisterRequest;
import com.hotelbooking.booking.dto.response.UserResponse;
import com.hotelbooking.booking.entity.AppUser;
import com.hotelbooking.booking.entity.Role;
import com.hotelbooking.booking.exception.EmailAlreadyRegisteredException;
import com.hotelbooking.booking.exception.UsernameAlreadyRegisteredException;
import com.hotelbooking.booking.mapper.AppUserMapper;
import com.hotelbooking.booking.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class RegistrationServiceUnitTest {
 AppUserRepository repo; AppUserMapper mapper; PasswordEncoder encoder; RegistrationService service;
 @BeforeEach void init(){repo=mock(AppUserRepository.class);mapper=mock(AppUserMapper.class);encoder=mock(PasswordEncoder.class);service=new RegistrationService(repo,mapper,encoder);}
 RegisterRequest request(){return new RegisterRequest("  USER@EXAMPLE.COM ","a-password","TeStUser",""," Jane "," Smith ");}
 @Test void registersNormalizedUserWithUserRoleAndEncodedPassword(){when(encoder.encode("a-password")).thenReturn("hash");when(repo.saveAndFlush(any())).thenAnswer(a->a.getArgument(0));service.register(request());var captor=org.mockito.ArgumentCaptor.forClass(AppUser.class);verify(repo).saveAndFlush(captor.capture());AppUser u=captor.getValue();assertEquals("user@example.com",u.getEmail());assertEquals("testuser",u.getUsername());assertEquals("hash",u.getPasswordHash());assertEquals(Role.USER,u.getRole());assertNull(u.getPhone());assertEquals("Jane",u.getFirstName());assertEquals("Smith",u.getLastName());}
 @Test void rejectsExistingUsernameBeforeEncoding(){when(repo.existsByUsername("testuser")).thenReturn(true);assertThrows(UsernameAlreadyRegisteredException.class,()->service.register(request()));verifyNoInteractions(encoder);verify(repo,never()).saveAndFlush(any());}
 @Test void rejectsExistingEmailBeforeEncoding(){when(repo.existsByEmail("user@example.com")).thenReturn(true);assertThrows(EmailAlreadyRegisteredException.class,()->service.register(request()));verifyNoInteractions(encoder);}
 @Test void rejectsMoreThan72Utf8Bytes(){String pw="é".repeat(37);var req=new RegisterRequest("x@example.com",pw,"testuser",null,"A","B");assertThrows(IllegalArgumentException.class,()->service.register(req));verifyNoInteractions(encoder);}
 @Test void convertsUniqueConstraintRaceIntoConflict(){when(encoder.encode(anyString())).thenReturn("hash");when(repo.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("unique"));ResponseStatusException e=assertThrows(ResponseStatusException.class,()->service.register(request()));assertEquals(HttpStatus.CONFLICT,e.getStatusCode());}
}
