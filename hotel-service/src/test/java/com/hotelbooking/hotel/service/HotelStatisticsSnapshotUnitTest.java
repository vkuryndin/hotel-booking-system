package com.hotelbooking.hotel.service;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class HotelStatisticsSnapshotUnitTest {

    @Test
    void statisticsUsesConsistentRepeatableReadSnapshot() throws NoSuchMethodException {
        Method method = HotelStatisticsService.class.getMethod("getStatistics", Long.class);
        Transactional tx = method.getAnnotation(Transactional.class);
        assertNotNull(tx, "Statistics aggregation must have an explicit transaction boundary");
        assertTrue(tx.readOnly(), "Statistics should use a read-only transaction");
        assertEquals(Isolation.REPEATABLE_READ, tx.isolation(),
                "Multiple COUNT/SUM queries must use a consistent repeatable-read snapshot");
    }
}
