# Architecture

## Runtime shape

The first CANOPYGUARD increment is a single-process Java 17+ JavaFX application. JavaFX owns presentation state; a single-thread scheduled executor advances the deterministic simulation; a bounded, single-thread JDBC executor writes operational events; and a separate I/O executor saves and restores JSON checkpoints. JavaFX updates are handed back using `Platform.runLater`.

```text
Emerald Reach GeoJSON -> Jackson validation -> JTS geometries -> MapCanvas
                                             |
JavaFX controls -> ordered simulation executor -> WildfireEngine -> immutable snapshots
                      |                              |                  |
                      |                              +---- risk --------+
                      +-> MissionService (atomic inventory reservation)
                      +-> RoutePlanner (risk/capacity constrained Dijkstra)
                      +-> bounded DatabaseService -> SQLite
                      +-> SessionStore -> versioned JSON / atomic replace
                                              |
                                              v
                                     JavaFX presentation
```

## Boundaries and invariants

- `WildfireEngine` owns seeded environmental cell values and state transitions. Its snapshots copy the cell list and contain no live engine objects.
- `RoutePlanner` validates edge endpoints and dimensions and excludes closed, over-capacity, inaccessible, or high-fire-risk edges.
- `MissionService` owns finite counts. Synchronized dispatch reserves one resource atomically; terminal transitions release once.
- `GeoJsonService` checks the FeatureCollection shape, required identifiers, duplicate IDs, and JTS geometry validity.
- `DatabaseService` uses parameterized SQL, short-lived JDBC connections, and a bounded writer queue. A write failure does not mutate or stop the in-memory simulation.
- `SessionStore` writes only simulation DTO state; temporary-file replacement is atomic where supported and unsupported schema versions fail explicitly.

## Relationship to BasinWatch

The project studied BasinWatch's separation of aggregate/domain services, deterministic event progression, immutable UI handoff, bounded asynchronous SQLite writes, atomic resource operations, explicit error boundaries, external user-data storage, and portable `jpackage` release. CANOPYGUARD replaces BasinWatch's Swing schematic with JavaFX and a JTS-backed GeoJSON map. This codebase does not copy BasinWatch's implementation.

## Current scope

The current UI directly connects the map, fire model, route candidates, community summaries, mission inventory, event timeline, SQLite, and checkpoint store. There is not yet a production event-ingestion queue, external-provider framework, ecological movement simulation, database migration framework beyond schema version initialization, or comprehensive incident snapshot restore for missions/community occupancy. Those omissions are intentional and are listed as limitations rather than implied working features.
