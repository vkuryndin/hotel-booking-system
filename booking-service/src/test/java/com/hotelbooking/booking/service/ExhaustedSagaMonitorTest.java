package com.hotelbooking.booking.service;

import com.hotelbooking.booking.repository.BookingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.List;

class ExhaustedSagaMonitorTest {
    @Test void checksExhaustedWithoutMutatingState() {
        BookingRepository repo = mock(BookingRepository.class);
        when(repo.findExhaustedRecovery(any(), any(), anyList(), anyInt(), any(Pageable.class)))
                .thenReturn(List.of());
        new ExhaustedSagaMonitor(repo).reportExhausted();
        verify(repo).findExhaustedRecovery(any(), any(), anyList(), eq(5), any(Pageable.class));
        verifyNoMoreInteractions(repo);
    }
}
