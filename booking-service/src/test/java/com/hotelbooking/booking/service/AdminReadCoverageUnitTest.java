package com.hotelbooking.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hotelbooking.booking.entity.AppUser;
import com.hotelbooking.booking.exception.UserOperationException;
import com.hotelbooking.booking.repository.AppUserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class AdminReadCoverageUnitTest {
  AppUserRepository users = mock(AppUserRepository.class);
  AdminUserReadService service = new AdminUserReadService(users);

  @Test
  void pageTooLargeRejected() {
    assertThrows(IllegalArgumentException.class, () -> service.getAllUsers(PageRequest.of(0, 101)));
    verifyNoInteractions(users);
  }

  @Test
  void missingUserRejected() {
    when(users.findById(9L)).thenReturn(Optional.empty());
    assertThrows(UserOperationException.class, () -> service.getUserById(9L));
  }

  @Test
  void pageUsesDeterministicIdSort() {
    when(users.findAll(any(Pageable.class))).thenReturn(Page.empty());

    assertTrue(service.getAllUsers(PageRequest.of(2, 15, Sort.by("email"))).isEmpty());

    ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);

    verify(users).findAll(captor.capture());

    Pageable pageable = captor.getValue();

    assertEquals(2, pageable.getPageNumber());
    assertEquals(15, pageable.getPageSize());
    assertNotNull(pageable.getSort().getOrderFor("id"));
  }

  @Test
  void existingUserMapped() {
    AppUser user = mock(AppUser.class);
    when(users.findById(1L)).thenReturn(Optional.of(user));
    assertNotNull(service.getUserById(1L));
  }
}
