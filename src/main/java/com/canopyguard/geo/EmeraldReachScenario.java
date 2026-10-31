package com.canopyguard.geo;

import com.canopyguard.domain.Community;
import com.canopyguard.domain.RoutePlanner;
import com.canopyguard.domain.Shelter;

import java.io.IOException;
import java.util.*;

public record EmeraldReachScenario(List<GeographicFeature> features, List<Community> communities,
                                   List<Shelter> shelters, RoutePlanner routes,
                                   double minLongitude, double minLatitude,
                                   double maxLongitude, double maxLatitude) {
    public EmeraldReachScenario {
        features = List.copyOf(features);
        communities = List.copyOf(communities);
        shelters = List.copyOf(shelters);
    }

    public static EmeraldReachScenario load() throws IOException {
        List<GeographicFeature> features;
        try (var stream = EmeraldReachScenario.class.getResourceAsStream("/scenarios/emerald-reach/region.geojson")) {
            if (stream == null) throw new IOException("Bundled Emerald Reach GeoJSON is missing");
            features = new GeoJsonService().read(stream);
        }
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (GeographicFeature feature : features) {
            minX = Math.min(minX, feature.geometry().getEnvelopeInternal().getMinX());
            minY = Math.min(minY, feature.geometry().getEnvelopeInternal().getMinY());
            maxX = Math.max(maxX, feature.geometry().getEnvelopeInternal().getMaxX());
            maxY = Math.max(maxY, feature.geometry().getEnvelopeInternal().getMaxY());
        }
        List<Community> communities = List.of(
                new Community("COMM-01", "Canopy Bend", 186, "PREPAREDNESS", true, -61.50, -2.48),
                new Community("COMM-02", "Riverwatch", 92, "MONITORING", false, -61.36, -2.58),
                new Community("COMM-03", "Northbank", 134, "MONITORING", true, -61.26, -2.35));
        List<Shelter> shelters = List.of(
                new Shelter("SHEL-01", "Emerald Field Station", 260, 74, true, -61.24, -2.55),
                new Shelter("SHEL-02", "South Ridge Hall", 180, 22, true, -61.53, -2.68));
        List<RoutePlanner.Node> nodes = List.of(
                new RoutePlanner.Node("COMM-01", "Canopy Bend"), new RoutePlanner.Node("COMM-02", "Riverwatch"),
                new RoutePlanner.Node("COMM-03", "Northbank"), new RoutePlanner.Node("JCT-01", "River Junction"),
                new RoutePlanner.Node("JCT-02", "Highland Junction"), new RoutePlanner.Node("SHEL-01", "Emerald Field Station"),
                new RoutePlanner.Node("SHEL-02", "South Ridge Hall"));
        List<RoutePlanner.Edge> edges = List.of(
                new RoutePlanner.Edge("ROAD-01", "COMM-01", "JCT-01", 4.2, 22, 220, 0.32, 0.25, false, "Forest road", true),
                new RoutePlanner.Edge("ROAD-02", "JCT-01", "SHEL-01", 6.1, 28, 240, 0.18, 0.16, false, "All-weather road", true),
                new RoutePlanner.Edge("ROAD-03", "COMM-01", "JCT-02", 7.8, 24, 180, 0.14, 0.12, false, "Highland road", true),
                new RoutePlanner.Edge("ROAD-04", "JCT-02", "SHEL-02", 5.2, 26, 180, 0.10, 0.12, false, "Highland road", true),
                new RoutePlanner.Edge("RIVER-01", "COMM-02", "JCT-01", 3.6, 12, 120, 0.20, 0.18, false, "Boat route", false),
                new RoutePlanner.Edge("ROAD-05", "COMM-03", "JCT-02", 4.1, 20, 150, 0.22, 0.15, false, "Forest track", true),
                new RoutePlanner.Edge("ROAD-06", "COMM-02", "COMM-03", 9.0, 18, 70, 0.72, 0.45, true, "Bridge crossing", false));
        return new EmeraldReachScenario(features, communities, shelters, new RoutePlanner(nodes, edges),
                minX, minY, maxX, maxY);
    }
}
