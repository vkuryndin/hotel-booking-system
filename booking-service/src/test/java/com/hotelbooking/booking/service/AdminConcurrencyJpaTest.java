package com.hotelbooking.booking.service;

import com.hotelbooking.booking.dto.request.UpdateAdminUserRequest;
import com.hotelbooking.booking.entity.AppUser;
import com.hotelbooking.booking.entity.Role;
import com.hotelbooking.booking.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Two ADMINs concurrently demoting each other must not leave the system without an ADMIN. */
@DataJpaTest(showSql = false)
@Import(AdminUserUpdateService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AdminConcurrencyJpaTest {
    @Autowired AppUserRepository users;
    @Autowired AdminUserUpdateService update;
    @MockitoBean PasswordEncoder encoder;

    @Test
    void competingDemotionsPreserveAtLeastOneAdministrator() throws Exception {
        long firstId = users.saveAndFlush(new AppUser("admin-a@example.com", "hash", Role.ADMIN,
                "admin_a", null, "A", "Admin")).getId();
        long secondId = users.saveAndFlush(new AppUser("admin-b@example.com", "hash", Role.ADMIN,
                "admin_b", null, "B", "Admin")).getId();
        UpdateAdminUserRequest demote = new UpdateAdminUserRequest(null, Role.USER,
                null, null, null, null);
        CountDownLatch start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        Callable<Boolean> aDemotesB = () -> {
            assertTrue(start.await(10, TimeUnit.SECONDS));
            try {
                update.updateUser(secondId, demote, firstId);
                return true;
            } catch (ResponseStatusException ex) {
                assertEquals(409, ex.getStatusCode().value());
                return false;
            }
        };
        Callable<Boolean> bDemotesA = () -> {
            assertTrue(start.await(10, TimeUnit.SECONDS));
            try {
                update.updateUser(firstId, demote, secondId);
                return true;
            } catch (ResponseStatusException ex) {
                assertEquals(409, ex.getStatusCode().value());
                return false;
            }
        };
        try {
            var first = pool.submit(aDemotesB);
            var second = pool.submit(bDemotesA);
            start.countDown();
            long successes = (first.get(20, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(20, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1L, successes);
            assertEquals(1L, users.findAll().stream()
                    .filter(u -> u.getRole() == Role.ADMIN).count());
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }
}
