package com.hotelbooking.hotel.service;
import com.hotelbooking.hotel.dto.request.HotelPresentationRequest;
import com.hotelbooking.hotel.entity.Hotel;
import com.hotelbooking.hotel.exception.HotelNotFoundException;
import com.hotelbooking.hotel.mapper.HotelMapper;
import com.hotelbooking.hotel.repository.HotelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class HotelPresentationServiceUnitTest {
 HotelRepository repo; HotelMapper mapper; HotelPresentationService service;
 @BeforeEach void init(){repo=mock(HotelRepository.class);mapper=mock(HotelMapper.class);service=new HotelPresentationService(repo,mapper);}
 @Test void noFieldsIsRejectedWithoutDbAccess(){assertThrows(IllegalArgumentException.class,()->service.update(5L,new HotelPresentationRequest(null,null,null,null,null)));verifyNoInteractions(repo,mapper);}
 @Test void unknownHotelIsRejected(){when(repo.findById(5L)).thenReturn(Optional.empty());assertThrows(HotelNotFoundException.class,()->service.update(5L,new HotelPresentationRequest("Updated",null,null,null,null)));verifyNoInteractions(mapper);}
 @Test void updateDelegatesToEntityAndMapper(){Hotel hotel=mock(Hotel.class);when(repo.findById(5L)).thenReturn(Optional.of(hotel));service.update(5L,new HotelPresentationRequest("Description",null,null,null,null));verify(hotel).changePresentation("Description",null,null,null,null);verify(mapper).toResponse(hotel);}
}
