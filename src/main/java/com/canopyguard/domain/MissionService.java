package com.canopyguard.domain;

import java.time.Instant;
import java.util.*;

public final class MissionService {
    public enum Status { ACTIVE, COMPLETED, CANCELLED }
    public record Mission(String id, String type, String target, String resourceType, String shelterId,
                          int evacuees, Status status,
                          int progressTicks, Instant createdAt, Instant completedAt) {}

    private final Map<String, Integer> available = new LinkedHashMap<>();
    private final Map<String, Integer> shelterCapacityRemaining = new LinkedHashMap<>();
    private final Map<String, Integer> shelterInitialOccupancy = new LinkedHashMap<>();
    private final Map<String, Mission> missions = new LinkedHashMap<>();
    private final Set<String> released = new HashSet<>();

    public MissionService() {
        available.put("BUS", 2);
        available.put("BOAT", 1);
        available.put("MEDICAL_TEAM", 1);
        available.put("WILDLIFE_TEAM", 1);
    }

    public synchronized void registerShelter(String shelterId, int capacity, int occupancy) {
        if (shelterId == null || shelterId.isBlank() || capacity < 0 || occupancy < 0 || occupancy > capacity)
            throw new IllegalArgumentException("Invalid shelter capacity");
        if (shelterCapacityRemaining.containsKey(shelterId)) throw new IllegalArgumentException("Shelter already registered: " + shelterId);
        shelterCapacityRemaining.put(shelterId, capacity - occupancy);
        shelterInitialOccupancy.put(shelterId, occupancy);
    }

    public synchronized Mission dispatch(String type, String target, String resourceType) {
        int stock = available.getOrDefault(resourceType, 0);
        if (stock <= 0) throw new IllegalStateException("No available " + resourceType.replace('_', ' ').toLowerCase(Locale.ROOT));
        available.put(resourceType, stock - 1);
        String id = "MSN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        Mission mission = new Mission(id, type, target, resourceType, null, 0, Status.ACTIVE, 0, Instant.now(), null);
        missions.put(id, mission);
        return mission;
    }

    public synchronized Mission dispatchEvacuation(String target, String resourceType, String shelterId, int evacuees) {
        if (evacuees <= 0) throw new IllegalArgumentException("Evacuation group must be positive");
        int stock = available.getOrDefault(resourceType, 0);
        int capacity = shelterCapacityRemaining.getOrDefault(shelterId, -1);
        if (capacity < evacuees) throw new IllegalStateException("Shelter has insufficient unreserved capacity");
        if (stock <= 0) throw new IllegalStateException("No available " + resourceType.replace('_', ' ').toLowerCase(Locale.ROOT));
        available.put(resourceType, stock - 1);
        shelterCapacityRemaining.put(shelterId, capacity - evacuees);
        String id = "MSN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        Mission mission = new Mission(id, "Community evacuation support", target, resourceType, shelterId,
                evacuees, Status.ACTIVE, 0, Instant.now(), null);
        missions.put(id, mission);
        return mission;
    }

    public synchronized List<Mission> advance() {
        List<Mission> updated = new ArrayList<>();
        for (Mission mission : List.copyOf(missions.values())) {
            if (mission.status() != Status.ACTIVE) continue;
            int progress = mission.progressTicks() + 34;
            Mission next = progress >= 100
                    ? new Mission(mission.id(), mission.type(), mission.target(), mission.resourceType(), mission.shelterId(),
                    mission.evacuees(), Status.COMPLETED, 100, mission.createdAt(), Instant.now())
                    : new Mission(mission.id(), mission.type(), mission.target(), mission.resourceType(), mission.shelterId(),
                    mission.evacuees(), Status.ACTIVE, progress, mission.createdAt(), null);
            missions.put(next.id(), next);
            if (next.status() == Status.COMPLETED) releaseOnce(next);
            updated.add(next);
        }
        return List.copyOf(updated);
    }

    public synchronized int available(String resourceType) { return available.getOrDefault(resourceType, 0); }
    public synchronized int shelterAvailable(String shelterId) { return shelterCapacityRemaining.getOrDefault(shelterId, 0); }
    public synchronized int shelterOccupancy(String shelterId) {
        if (!shelterInitialOccupancy.containsKey(shelterId)) throw new IllegalArgumentException("Unknown shelter " + shelterId);
        return shelterInitialOccupancy.get(shelterId) + missions.values().stream()
                .filter(m -> shelterId.equals(m.shelterId()) && m.status() != Status.CANCELLED)
                .mapToInt(Mission::evacuees).sum();
    }
    public synchronized List<Mission> missions() { return List.copyOf(missions.values()); }

    public synchronized Mission cancel(String missionId) {
        Mission current = missions.get(missionId);
        if (current == null) throw new IllegalArgumentException("Unknown mission " + missionId);
        if (current.status() != Status.ACTIVE) return current;
        Mission cancelled = new Mission(current.id(), current.type(), current.target(), current.resourceType(), current.shelterId(),
                current.evacuees(), Status.CANCELLED, current.progressTicks(), current.createdAt(), Instant.now());
        missions.put(missionId, cancelled);
        if (current.shelterId() != null) shelterCapacityRemaining.merge(current.shelterId(), current.evacuees(), Integer::sum);
        releaseOnce(cancelled);
        return cancelled;
    }

    private void releaseOnce(Mission mission) {
        if (released.add(mission.id())) available.merge(mission.resourceType(), 1, Integer::sum);
    }
}
