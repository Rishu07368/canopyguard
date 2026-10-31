package com.canopyguard.geo;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class GeoJsonServiceTest {
    @Test void bundledScenarioFeaturesHaveValidSpatialGeometry() throws Exception {
        var scenario = EmeraldReachScenario.load();
        assertTrue(scenario.features().size() >= 10);
        assertTrue(scenario.features().stream().allMatch(feature -> feature.geometry().isValid()));
        assertTrue(scenario.features().stream().anyMatch(feature -> feature.kind().equals("community")));
    }

    @Test void malformedGeoJsonFeatureIsRejected() {
        String invalid = "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"properties\":{\"id\":\"a\",\"kind\":\"forest\",\"name\":\"A\"},\"geometry\":{\"type\":\"Unknown\",\"coordinates\":[]}}]}";
        assertThrows(Exception.class, () -> new GeoJsonService().read(new ByteArrayInputStream(invalid.getBytes(StandardCharsets.UTF_8))));
    }
}
