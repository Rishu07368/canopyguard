package com.canopyguard.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseServiceTest {
    @TempDir Path temp;
    @Test void asynchronousEventsArePersistedToSqlite() throws Exception {
        Path file = temp.resolve("data").resolve("test.db");
        try (DatabaseService service = new DatabaseService(file)) {
            service.recordEvent("TEST", "event", 3).get();
            try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file);
                 var statement = connection.prepareStatement("SELECT category, tick FROM operational_events");
                 var rows = statement.executeQuery()) {
                assertTrue(rows.next());
                assertEquals("TEST", rows.getString(1));
                assertEquals(3, rows.getInt(2));
            }
        }
    }
}
