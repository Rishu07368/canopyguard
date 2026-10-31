package com.canopyguard.persistence;

import com.canopyguard.engine.WildfireEngine;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;

public final class SessionStore {
    private static final int SCHEMA_VERSION = 1;
    private final ObjectMapper mapper = new ObjectMapper();
    public record SavedSession(int schemaVersion, String scenarioId, String savedAt, WildfireEngine.Snapshot simulation) {}

    public void save(Path target, WildfireEngine.Snapshot snapshot) throws IOException {
        SavedSession session = new SavedSession(SCHEMA_VERSION, "emerald-reach", Instant.now().toString(), snapshot);
        Path absolute = target.toAbsolutePath();
        Files.createDirectories(absolute.getParent());
        Path temporary = Files.createTempFile(absolute.getParent(), "canopyguard-", ".tmp");
        try {
            mapper.writeValue(temporary.toFile(), session);
            try {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public WildfireEngine.Snapshot load(Path source, int expectedWidth, int expectedHeight) throws IOException {
        SavedSession session = mapper.readValue(source.toFile(), SavedSession.class);
        if (session == null || session.schemaVersion() != SCHEMA_VERSION || !"emerald-reach".equals(session.scenarioId())
                || session.simulation() == null || session.simulation().width() != expectedWidth
                || session.simulation().height() != expectedHeight)
            throw new IOException("Unsupported or invalid CANOPYGUARD save file");
        return session.simulation();
    }

    public String toJson(WildfireEngine.Snapshot snapshot) throws IOException {
        return mapper.writeValueAsString(new SavedSession(SCHEMA_VERSION, "emerald-reach", Instant.now().toString(), snapshot));
    }
}
