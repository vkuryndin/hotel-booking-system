package com.hotelbooking.booking.service;

import com.hotelbooking.booking.entity.AppUser;
import com.hotelbooking.booking.entity.Role;
import com.hotelbooking.booking.repository.AppUserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql = false)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AppUserOptimisticLockJpaTest {

    @Autowired private AppUserRepository users;
    @Autowired private EntityManagerFactory emf;

    @Test
    void staleProfileUpdateMustNotRestoreDemotedAdminOrOldTokenVersion() {
        Long id = users.saveAndFlush(new AppUser(
                "race-profile@example.com", "hash", Role.ADMIN,
                "race_profile", null, "First", "Last")).getId();

        EntityManager staleSession = emf.createEntityManager();
        try {
            AppUser stale = staleSession.find(AppUser.class, id);
            assertEquals(Role.ADMIN, stale.getRole());
            assertEquals(0, stale.getTokenVersion());

            // Committed change from a different request.
            EntityManager adminSession = emf.createEntityManager();
            try {
                adminSession.getTransaction().begin();
                AppUser admin = adminSession.find(AppUser.class, id);
                admin.changeRole(Role.USER);
                adminSession.getTransaction().commit();
            } finally {
                adminSession.close();
            }

            staleSession.getTransaction().begin();
            stale.changeProfile("race_profile", null, "Stale", "Last");
            assertThrows(RuntimeException.class, () -> {
                staleSession.flush();
                staleSession.getTransaction().commit();
            }, "A stale entity version must fail instead of overwriting the demotion");
            if (staleSession.getTransaction().isActive()) staleSession.getTransaction().rollback();

            AppUser persisted = users.findById(id).orElseThrow();
            assertEquals(Role.USER, persisted.getRole());
            assertEquals(1, persisted.getTokenVersion());
            assertEquals("First", persisted.getFirstName());
        } finally {
            if (staleSession.getTransaction().isActive()) staleSession.getTransaction().rollback();
            staleSession.close();
            users.deleteById(id);
        }
    }

    @Test
    void stalePasswordUpdateMustNotClearAdminPasswordResetFlag() {
        Long id = users.saveAndFlush(new AppUser(
                "race-password@example.com", "old", Role.USER,
                "race_password", null, "First", "Last")).getId();
        EntityManager staleSession = emf.createEntityManager();
        try {
            AppUser stale = staleSession.find(AppUser.class, id);
            EntityManager adminSession = emf.createEntityManager();
            try {
                adminSession.getTransaction().begin();
                AppUser admin = adminSession.find(AppUser.class, id);
                admin.changePasswordHash("reset-password-hash");
                admin.requirePasswordChange();
                adminSession.getTransaction().commit();
            } finally {
                adminSession.close();
            }

            staleSession.getTransaction().begin();
            stale.completePasswordChange("stale-new-password-hash");
            assertThrows(RuntimeException.class, () -> {
                staleSession.flush();
                staleSession.getTransaction().commit();
            });
            if (staleSession.getTransaction().isActive()) staleSession.getTransaction().rollback();

            AppUser persisted = users.findById(id).orElseThrow();
            assertEquals("reset-password-hash", persisted.getPasswordHash());
            assertTrue(persisted.isPasswordChangeRequired());
            assertEquals(1, persisted.getTokenVersion());
        } finally {
            if (staleSession.getTransaction().isActive()) staleSession.getTransaction().rollback();
            staleSession.close();
            users.deleteById(id);
        }
    }
}
