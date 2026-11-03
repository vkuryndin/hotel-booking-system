package com.hotelbooking.booking.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

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
