package com.hotelbooking.booking.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hotelbooking.booking.service.AdminUserReadService;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

class AdminUserSearchControllerTest {

  @Test
  void userSearchEndpointRemainsAdminOnly() throws Exception {
    Method endpoint =
        AdminUserReadController.class.getMethod("getAllUsers", Pageable.class, String.class);
    assertNotNull(endpoint.getAnnotation(GetMapping.class));
    PreAuthorize preAuthorize = endpoint.getAnnotation(PreAuthorize.class);
    assertNotNull(preAuthorize);
    assertEquals("hasRole('ADMIN')", preAuthorize.value());
    RequestParam query = endpoint.getParameters()[1].getAnnotation(RequestParam.class);
    assertNotNull(query);
    assertEquals("q", query.name());
    assertTrue(!query.required());
  }

  @Test
  void controllerPassesOptionalQueryToExistingReadService() {
    AdminUserReadService service = mock(AdminUserReadService.class);
    AdminUserReadController controller = new AdminUserReadController(service);
    Pageable page = PageRequest.of(1, 20);
    when(service.getAllUsers(page, "petrov")).thenReturn(Page.empty());

    assertTrue(controller.getAllUsers(page, "petrov").isEmpty());

    verify(service).getAllUsers(page, "petrov");
  }
}
