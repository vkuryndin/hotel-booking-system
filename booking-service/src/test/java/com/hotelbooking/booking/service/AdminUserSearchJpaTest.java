package com.hotelbooking.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hotelbooking.booking.entity.AppUser;
import com.hotelbooking.booking.entity.Role;
import com.hotelbooking.booking.repository.AppUserRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@DataJpaTest(showSql = false)
class AdminUserSearchJpaTest {

  @Autowired AppUserRepository users;

  @BeforeEach
  void setUp() {
    users.save(
        new AppUser("alpha@example.com", "hash", Role.USER, "alpha", null, "Irina", "Petrova"));
    users.save(new AppUser("beta@example.com", "hash", Role.USER, "beta", null, "Oleg", "Smirnov"));
    users.save(
        new AppUser(
            "gamma@example.com", "hash", Role.USER, "special_1", null, "Maria", "Sidorova"));
    users.save(
        new AppUser("delta@domain.test", "hash", Role.USER, "delta", null, "Nina", "Ivanova"));
    users.flush();
  }

  @Test
  void matchesUsernameFirstNameLastNameAndEmailCaseInsensitively() {
    assertEquals("special_1", find("%special_1%").getContent().get(0).getUsername());
    assertEquals("alpha", find("%irina%").getContent().get(0).getUsername());
    assertEquals("alpha", find("%irina petrova%").getContent().get(0).getUsername());
    assertEquals("beta", find("%smirnov%").getContent().get(0).getUsername());
    assertEquals("delta", find("%domain.test%").getContent().get(0).getUsername());
    assertTrue(find("%nonexistent%").isEmpty());
  }

  @Test
  void literalUnderscoreIsNotWildcard() {
    assertEquals(1, find("%special!_1%").getTotalElements());
    assertEquals(0, find("%special!_2%").getTotalElements());
  }

  @Test
  void paginationRestrictsResultSetAndKeepsIdOrder() {
    Page<AppUser> firstPage =
        users.findByAdminSearchPattern("%example.com%", PageRequest.of(0, 2, Sort.by("id")));
    assertEquals(3L, firstPage.getTotalElements());
    assertEquals(List.of("alpha", "beta"), firstPage.map(AppUser::getUsername).getContent());
  }

  private Page<AppUser> find(String pattern) {
    return users.findByAdminSearchPattern(pattern, PageRequest.of(0, 20, Sort.by("id")));
  }
}
