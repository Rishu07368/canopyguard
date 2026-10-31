package com.canopyguard.persistence;

import com.canopyguard.engine.WildfireEngine;
import com.canopyguard.domain.FireCell;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;

import static org.junit.jupiter.api.Assertions.*;

class SessionStoreTest {
    @TempDir Path temp;

    @Test void sessionRoundTripRestoresSimulationCells() throws Exception {
        WildfireEngine engine = new WildfireEngine(new WildfireEngine.Settings(8, 8, 4, 90, 14, 35, .9), 3, 3);
        engine.advance(); engine.advance();
        SessionStore store = new SessionStore();
        Path file = temp.resolve("saved.json");
        store.save(file, engine.snapshot());
        var saved = store.load(file, 8, 8);
        var restored = WildfireEngine.restore(new WildfireEngine.Settings(8, 8, 4, 90, 14, 35, .9), saved);
        assertEquals(engine.snapshot(), restored.snapshot());
    }

    @Test void invalidSaveSchemaIsRejected() throws Exception {
        Path file = temp.resolve("invalid.json");
        Files.writeString(file, "{\"schemaVersion\":9,\"scenarioId\":\"other\",\"savedAt\":\"now\",\"simulation\":null}");
        assertThrows(Exception.class, () -> new SessionStore().load(file, 8, 8));
    }

    @Test void duplicatedGridCellsAreRejectedDuringRestore() {
        WildfireEngine engine = new WildfireEngine(new WildfireEngine.Settings(4, 4, 7, 0, 0, 40, 1), 1, 1);
        var source = engine.snapshot();
        var cells = new java.util.ArrayList<>(source.cells());
        FireCell first = cells.get(0);
        cells.set(1, new FireCell(first.x(), first.y(), first.fuel(), first.moisture(), first.slope(), first.state(), first.intensity(), first.smoke()));
        var invalid = new WildfireEngine.Snapshot(source.tick(), source.width(), source.height(), cells, source.windDegrees(), source.windKph(), source.humidityPercent());
        assertThrows(IllegalArgumentException.class, () -> WildfireEngine.restore(
                new WildfireEngine.Settings(4, 4, 7, 0, 0, 40, 1), invalid));
    }
}
