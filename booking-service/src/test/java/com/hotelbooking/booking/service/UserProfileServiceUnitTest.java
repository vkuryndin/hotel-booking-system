package com.hotelbooking.booking.service;
import com.hotelbooking.booking.dto.request.UpdateMyProfileRequest;
import com.hotelbooking.booking.entity.AppUser;
import com.hotelbooking.booking.entity.Role;
import com.hotelbooking.booking.exception.UsernameAlreadyRegisteredException;
import com.hotelbooking.booking.mapper.AppUserMapper;
import com.hotelbooking.booking.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import com.hotelbooking.booking.exception.UserOperationException;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class UserProfileServiceUnitTest {
 AppUserRepository repo; AppUserMapper mapper; UserProfileService service; AppUser user;
 @BeforeEach void init(){repo=mock(AppUserRepository.class);mapper=mock(AppUserMapper.class);service=new UserProfileService(repo,mapper);user=new AppUser("x@example.com","hash",Role.USER,"oldname","+12345678901","Old","Name");}
 @Test void readsOwnProfile(){when(repo.findById(1L)).thenReturn(Optional.of(user));service.getMyProfile(1L);verify(mapper).toResponse(user);}
 @Test void readMissingUserIsUnauthorized(){var e=assertThrows(UserOperationException.class,()->service.getMyProfile(1L));assertEquals(UserOperationException.Reason.USER_NOT_AUTHENTICATED,e.reason());}
 @Test void emptyPatchIsRejected(){assertThrows(IllegalArgumentException.class,()->service.updateMyProfile(1L,new UpdateMyProfileRequest(null,null,null,null)));verifyNoInteractions(repo);}
 @Test void usernameCollisionIsRejected(){when(repo.findById(1L)).thenReturn(Optional.of(user));when(repo.existsByUsername("other")).thenReturn(true);assertThrows(UsernameAlreadyRegisteredException.class,()->service.updateMyProfile(1L,new UpdateMyProfileRequest("OTHER",null,null,null)));verify(repo,never()).saveAndFlush(any());}
 @Test void normalizationAndPhoneClearingArePersisted(){when(repo.findById(1L)).thenReturn(Optional.of(user));service.updateMyProfile(1L,new UpdateMyProfileRequest("NEWNAME",""," Jane "," Doe "));assertEquals("newname",user.getUsername());assertNull(user.getPhone());assertEquals("Jane",user.getFirstName());assertEquals("Doe",user.getLastName());verify(repo).saveAndFlush(user);}
 @Test void saveConstraintCollisionIsTranslated(){when(repo.findById(1L)).thenReturn(Optional.of(user));when(repo.saveAndFlush(user)).thenThrow(new DataIntegrityViolationException("unique"));assertThrows(UsernameAlreadyRegisteredException.class,()->service.updateMyProfile(1L,new UpdateMyProfileRequest(null,null,"New",null)));}
}
