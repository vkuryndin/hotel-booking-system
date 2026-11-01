package com.hotelbooking.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hotelbooking.booking.dto.response.AdminUserResponse;
import com.hotelbooking.booking.entity.AppUser;
import com.hotelbooking.booking.entity.Role;
import com.hotelbooking.booking.repository.AppUserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class AdminUserSearchServiceTest {

  private final AppUserRepository users = mock(AppUserRepository.class);
  private final AdminUserReadService service = new AdminUserReadService(users);

  @Test
  void nonblankSearchIsTrimmedLowercasedAndKeepsPagination() {
    AppUser user = new AppUser("ana@example.com", "hash", Role.USER, "ana", null, "Ana", "Ivanova");
    when(users.findByAdminSearchPattern(any(String.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(user)));

    Page<AdminUserResponse> result =
        service.getAllUsers(PageRequest.of(2, 15, Sort.by("email")), "  IVA  ");

    assertEquals(1, result.getContent().size());
    assertEquals("ana", result.getContent().get(0).username());
    ArgumentCaptor<String> pattern = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
    verify(users).findByAdminSearchPattern(pattern.capture(), page.capture());
    assertEquals("%iva%", pattern.getValue());
    assertEquals(2, page.getValue().getPageNumber());
    assertEquals(15, page.getValue().getPageSize());
    assertEquals(Sort.by("id"), page.getValue().getSort());
  }

  @Test
  void wildcardsAreEscapedAndCannotExpandSearch() {
    when(users.findByAdminSearchPattern(any(String.class), any(Pageable.class)))
        .thenReturn(Page.empty());

    service.getAllUsers(PageRequest.of(0, 10), " !_% ");

    verify(users).findByAdminSearchPattern(org.mockito.ArgumentMatchers.eq("%!!!_!%%"), any());
  }

  @Test
  void emptySearchRetainsOriginalRepositoryPath() {
    when(users.findAll(any(Pageable.class))).thenReturn(Page.empty());

    assertTrue(service.getAllUsers(PageRequest.of(0, 20), "   ").isEmpty());
    verify(users).findAll(any(Pageable.class));
  }

  @Test
  void pageSizeLimitAlsoAppliesWhenSearching() {
    assertThrows(
        IllegalArgumentException.class, () -> service.getAllUsers(PageRequest.of(0, 101), "ivan"));
    verifyNoInteractions(users);
  }
}
