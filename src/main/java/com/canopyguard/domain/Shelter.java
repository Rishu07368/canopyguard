package com.canopyguard.domain;

public record Shelter(String id, String name, int capacity, int occupancy, boolean accessible,
                      double longitude, double latitude) {
    public Shelter {
        if (capacity < 0 || occupancy < 0 || occupancy > capacity) throw new IllegalArgumentException("Invalid shelter occupancy");
    }
    public int availableCapacity() { return capacity - occupancy; }
}
