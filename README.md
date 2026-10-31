# CANOPYGUARD

CANOPYGUARD is an offline Java desktop prototype for wildfire situation awareness, evacuation planning, and rainforest habitat protection. Its bundled **Emerald Reach** region is fictional. It is not a certified forecast or an emergency alert/dispatch system.

## Run locally

Requirements: Java 17+ and Maven 3.8+.

```powershell
.\scripts\test.ps1
.\scripts\run.ps1
```

Or build and launch directly:

```powershell
mvn package
java -jar target\canopyguard-1.0.0.jar
```

The first launch stores local data under `%APPDATA%\CanopyGuard\data` (database, saves, and reports). The app runs without an internet connection. If SQLite is unavailable, the in-memory simulation remains available and the UI reports the persistence issue.

## Current working features

- JavaFX operations shell with a coordinate-based Canvas map, independent vector layers, feature inspection, zoom, and pan.
- Bundled fictional GeoJSON loaded and validated with JTS.
- Reproducible grid-based fire spread and wind-influenced smoke heuristic, plus synthetic wind shifts.
- Dijkstra route candidates with exposure, capacity, accessibility, and closure constraints.
- Aggregated community information, shelter capacity display, finite atomic mission reservations, and chronological event capture.
- Asynchronous SQLite event/snapshot history and validated, atomic JSON simulation checkpoints.

All map, weather, fire, and community values are synthetic scenario data. No external feed is configured.

## Limitations

This initial release is a working planning prototype, not the full operational scope described in the project brief. See [SAFETY_AND_LIMITATIONS.md](docs/SAFETY_AND_LIMITATIONS.md) for missing capabilities and use boundaries. No evacuation orders, public messages, real dispatch, real Indigenous locations, or live observations are generated.

## Windows package

On a Windows build machine with a full JDK (including `jpackage`) and Maven:

```powershell
.\scripts\package-windows.ps1
```

The script tests, packages a self-contained app image with a bundled runtime, zips it, and checks for the launcher. See [DEPLOYMENT.md](docs/DEPLOYMENT.md).
