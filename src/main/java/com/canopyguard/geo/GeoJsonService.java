package com.canopyguard.geo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.geojson.GeoJsonReader;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public final class GeoJsonService {
    private final ObjectMapper mapper = new ObjectMapper();
    private final GeoJsonReader reader = new GeoJsonReader();

    public List<GeographicFeature> read(InputStream input) throws IOException {
        Objects.requireNonNull(input);
        JsonNode root = mapper.readTree(input);
        if (root == null || !"FeatureCollection".equals(root.path("type").asText()))
            throw new IOException("GeoJSON root must be a FeatureCollection");
        JsonNode features = root.path("features");
        if (!features.isArray()) throw new IOException("GeoJSON features must be an array");
        List<GeographicFeature> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonNode feature : features) {
            if (!"Feature".equals(feature.path("type").asText())) throw new IOException("FeatureCollection contains a non-Feature record");
            JsonNode propertiesNode = feature.path("properties");
            String id = propertiesNode.path("id").asText("");
            String kind = propertiesNode.path("kind").asText("");
            String name = propertiesNode.path("name").asText("");
            if (id.isBlank() || kind.isBlank() || name.isBlank()) throw new IOException("Each feature requires id, kind, and name properties");
            if (!ids.add(id)) throw new IOException("Duplicate GeoJSON feature id: " + id);
            try {
                Geometry geometry = reader.read(mapper.writeValueAsString(feature.path("geometry")));
                Map<String, String> properties = new LinkedHashMap<>();
                propertiesNode.fields().forEachRemaining(entry -> properties.put(entry.getKey(), entry.getValue().asText()));
                result.add(new GeographicFeature(id, kind, name, geometry, properties));
            } catch (ParseException e) {
                throw new IOException("Invalid geometry in feature " + id, e);
            }
        }
        return List.copyOf(result);
    }
}
