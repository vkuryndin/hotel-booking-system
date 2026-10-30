package com.hotelbooking.hotel.service;

import com.hotelbooking.hotel.entity.Hotel;
import com.hotelbooking.hotel.entity.HotelAmenity;
import com.hotelbooking.hotel.repository.HotelRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql = false)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class HotelOptimisticLockJpaTest {

    @Autowired private HotelRepository hotels;
    @Autowired private EntityManagerFactory emf;

    @Test
    void stalePresentationMustNotOverwriteHotelNameAndAddress() {
        Long id = newHotel();
        EntityManager staleSession = emf.createEntityManager();
        try {
            Hotel stale = staleSession.find(Hotel.class, id);
            assertNotNull(stale.getVersion());

            EntityManager freshSession = emf.createEntityManager();
            try {
                freshSession.getTransaction().begin();
                Hotel fresh = freshSession.find(Hotel.class, id);
                fresh.updateDetails("New Name", "New Address");
                freshSession.getTransaction().commit();
            } finally {
                freshSession.close();
            }

            staleSession.getTransaction().begin();
            stale.changePresentation("Stale description", new BigDecimal("4.5"),
                    null, LocalTime.of(15, 0), null);
            assertThrows(RuntimeException.class, () -> {
                staleSession.flush();
                staleSession.getTransaction().commit();
            });
            rollbackIfActive(staleSession);

            Hotel saved = hotels.findById(id).orElseThrow();
            assertEquals("New Name", saved.getName());
            assertEquals("New Address", saved.getAddress());
            assertNull(saved.getDescription());
        } finally {
            rollbackIfActive(staleSession);
            staleSession.close();
            hotels.deleteById(id);
        }
    }

    @Test
    void staleDetailsMustNotOverwritePresentation() {
        Long id = newHotel();
        EntityManager staleSession = emf.createEntityManager();
        try {
            Hotel stale = staleSession.find(Hotel.class, id);
            EntityManager freshSession = emf.createEntityManager();
            try {
                freshSession.getTransaction().begin();
                Hotel fresh = freshSession.find(Hotel.class, id);
                fresh.changePresentation("Updated description", new BigDecimal("4.8"),
                        Set.of(HotelAmenity.WIFI), null, null);
                freshSession.getTransaction().commit();
            } finally {
                freshSession.close();
            }

            staleSession.getTransaction().begin();
            stale.updateDetails("Stale Name", "Stale Address");
            assertThrows(RuntimeException.class, () -> {
                staleSession.flush();
                staleSession.getTransaction().commit();
            });
            rollbackIfActive(staleSession);

            EntityManager verifySession = emf.createEntityManager();
            try {
                verifySession.getTransaction().begin();
                Hotel saved = verifySession.find(Hotel.class, id);
                assertEquals("Updated description", saved.getDescription());
                assertEquals(new BigDecimal("4.8"), saved.getRating());
                assertTrue(saved.getAmenities().contains(HotelAmenity.WIFI));
                assertNotEquals("Stale Name", saved.getName());
                verifySession.getTransaction().commit();
            } finally {
                rollbackIfActive(verifySession);
                verifySession.close();
            }
        } finally {
            rollbackIfActive(staleSession);
            staleSession.close();
            hotels.deleteById(id);
        }
    }

    private Long newHotel() {
        return hotels.saveAndFlush(new Hotel("Hotel-" + UUID.randomUUID(), "Initial Address")).getId();
    }

    private static void rollbackIfActive(EntityManager em) {
        if (em.getTransaction().isActive()) em.getTransaction().rollback();
    }
}
