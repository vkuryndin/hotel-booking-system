package com.hotelbooking.booking.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.hotelbooking.booking.repository.BookingRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class ExhaustedSagaMonitorTest {
  @Test
  void checksExhaustedWithoutMutatingState() {
    BookingRepository repo = mock(BookingRepository.class);
    when(repo.findExhaustedRecovery(any(), any(), anyList(), anyInt(), any(Pageable.class)))
        .thenReturn(List.of());
    new ExhaustedSagaMonitor(repo).reportExhausted();
    verify(repo).findExhaustedRecovery(any(), any(), anyList(), eq(5), any(Pageable.class));
    verifyNoMoreInteractions(repo);
  }
}
