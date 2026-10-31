package com.canopyguard.domain;

import org.junit.jupiter.api.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MissionServiceTest {
    @Test void simultaneousReservationsNeverOversubscribeFleet() throws Exception {
        MissionService service = new MissionService();
        int workers = 12;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        for (int i = 0; i < workers; i++) executor.submit(() -> {
            try { start.await(); service.dispatch("Evacuation", "Canopy Bend", "BUS"); successes.incrementAndGet(); }
            catch (IllegalStateException ignored) { }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        start.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
        assertEquals(2, successes.get());
        assertEquals(0, service.available("BUS"));
    }

    @Test void missionCompletionReturnsResourceOnlyOnce() {
        MissionService service = new MissionService();
        var mission = service.dispatch("Evacuation", "Riverwatch", "BOAT");
        assertEquals(0, service.available("BOAT"));
        service.advance(); service.advance(); service.advance(); service.advance();
        assertEquals(MissionService.Status.COMPLETED, service.missions().get(0).status());
        assertEquals(1, service.available("BOAT"));
        assertEquals(MissionService.Status.ACTIVE, mission.status());
    }

    @Test void failedEvacuationReservationLeavesFleetAndShelterUnchanged() {
        MissionService service = new MissionService();
        service.registerShelter("S-1", 50, 10);
        assertThrows(IllegalStateException.class, () -> service.dispatchEvacuation("Canopy Bend", "BUS", "S-1", 41));
        assertEquals(2, service.available("BUS"));
        assertEquals(40, service.shelterAvailable("S-1"));
    }

    @Test void evacuationMissionAtomicallyReservesShelterAndVehicle() {
        MissionService service = new MissionService();
        service.registerShelter("S-1", 200, 20);
        var mission = service.dispatchEvacuation("Canopy Bend", "BUS", "S-1", 120);
        assertEquals(1, service.available("BUS"));
        assertEquals(60, service.shelterAvailable("S-1"));
        assertEquals(140, service.shelterOccupancy("S-1"));
        service.cancel(mission.id());
        assertEquals(2, service.available("BUS"));
        assertEquals(180, service.shelterAvailable("S-1"));
        assertEquals(20, service.shelterOccupancy("S-1"));
    }
}
