package com.hotelbooking.hotel.service;
import com.hotelbooking.hotel.dto.request.RoomDetailsRequest;
import com.hotelbooking.hotel.entity.Room;
import com.hotelbooking.hotel.exception.RoomNotFoundException;
import com.hotelbooking.hotel.mapper.RoomMapper;
import com.hotelbooking.hotel.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class RoomDetailsServiceUnitTest {
 RoomRepository repo; RoomMapper mapper; RoomDetailsService service;
 @BeforeEach void init(){repo=mock(RoomRepository.class);mapper=mock(RoomMapper.class);service=new RoomDetailsService(repo,mapper);}
 @Test void emptyPatchRejectedBeforeDatabaseAccess(){assertThrows(IllegalArgumentException.class,()->service.update(5L,new RoomDetailsRequest(null,null,null,null)));verifyNoInteractions(repo,mapper);}
 @Test void unknownRoomRejected(){when(repo.findByIdForUpdate(5L)).thenReturn(Optional.empty());assertThrows(RoomNotFoundException.class,()->service.update(5L,new RoomDetailsRequest(null,3,null,null)));verifyNoInteractions(mapper);}
 @Test void updateUsesLockedReadAndMapsEntity(){Room room=mock(Room.class);when(repo.findByIdForUpdate(5L)).thenReturn(Optional.of(room));service.update(5L,new RoomDetailsRequest(null,3,null,null));verify(room).changeRoomDetails(null,3,null,null);verify(mapper).toResponse(room);verify(repo).findByIdForUpdate(5L);}
}
