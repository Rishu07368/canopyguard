package com.canopyguard.persistence;

import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class DatabaseService implements AutoCloseable {
    private final Path databasePath;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(256), runnable -> {
        Thread thread = new Thread(runnable, "canopyguard-database");
        thread.setDaemon(true);
        return thread;
    }, new ThreadPoolExecutor.AbortPolicy());
    private final AtomicInteger pending = new AtomicInteger();
    private volatile String lastError = "";

    public DatabaseService(Path databasePath) throws SQLException, java.io.IOException {
        this.databasePath = databasePath.toAbsolutePath();
        Files.createDirectories(this.databasePath.getParent());
        initialize();
    }

    private Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 3000");
        }
        return connection;
    }

    private void initialize() throws SQLException {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER NOT NULL)");
            statement.execute("INSERT INTO schema_version(version) SELECT 1 WHERE NOT EXISTS (SELECT 1 FROM schema_version)");
            statement.execute("CREATE TABLE IF NOT EXISTS operational_events (id INTEGER PRIMARY KEY, occurred_at TEXT NOT NULL, category TEXT NOT NULL, message TEXT NOT NULL, tick INTEGER NOT NULL)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_events_time ON operational_events(occurred_at)");
            statement.execute("CREATE TABLE IF NOT EXISTS simulation_snapshots (id INTEGER PRIMARY KEY, saved_at TEXT NOT NULL, scenario_id TEXT NOT NULL, tick INTEGER NOT NULL, payload TEXT NOT NULL)");
        }
    }

    public CompletableFuture<Void> recordEvent(String category, String message, int tick) {
        return enqueue(() -> {
            try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO operational_events(occurred_at, category, message, tick) VALUES(?,?,?,?)")) {
                statement.setString(1, Instant.now().toString());
                statement.setString(2, category);
                statement.setString(3, message);
                statement.setInt(4, tick);
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> recordSnapshot(String scenarioId, int tick, String payload) {
        return enqueue(() -> {
            try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO simulation_snapshots(saved_at, scenario_id, tick, payload) VALUES(?,?,?,?)")) {
                statement.setString(1, Instant.now().toString());
                statement.setString(2, scenarioId);
                statement.setInt(3, tick);
                statement.setString(4, payload);
                statement.executeUpdate();
            }
        });
    }

    private CompletableFuture<Void> enqueue(SqlRunnable job) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        pending.incrementAndGet();
        try {
            executor.execute(() -> {
                try {
                    job.run();
                    result.complete(null);
                } catch (Exception e) {
                    lastError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                    result.completeExceptionally(e);
                } finally {
                    pending.decrementAndGet();
                }
            });
        } catch (RejectedExecutionException e) {
            pending.decrementAndGet();
            lastError = "Database queue is full or shutting down";
            result.completeExceptionally(e);
        }
        return result;
    }

    public int pendingWrites() { return pending.get(); }
    public String lastError() { return lastError; }
    public boolean isHealthy() { return lastError.isBlank(); }

    @Override public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(8, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) throw new IllegalStateException("Database worker did not stop");
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while draining database writes", e);
        }
    }

    @FunctionalInterface private interface SqlRunnable { void run() throws Exception; }
}
