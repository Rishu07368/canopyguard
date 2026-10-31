package com.canopyguard.geo;

import org.locationtech.jts.geom.Geometry;

import java.util.Map;

public record GeographicFeature(String id, String kind, String name, Geometry geometry, Map<String, String> properties) {
    public GeographicFeature {
        properties = Map.copyOf(properties);
        if (geometry == null || geometry.isEmpty() || !geometry.isValid())
            throw new IllegalArgumentException("Invalid geometry for feature " + id);
    }
}
