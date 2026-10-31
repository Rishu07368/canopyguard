# Data model

## Spatial data

`src/main/resources/scenarios/emerald-reach/region.geojson` is an RFC 7946-style FeatureCollection in WGS 84 longitude/latitude coordinates. Every feature has a unique `id`, `kind`, and `name`. Feature properties also carry scenario-only values such as aggregated population estimates or generalized habitat labels. JTS geometries back feature rendering and feature inspection.

## Simulation

Each fire-grid cell contains fuel, moisture, slope, fire state, fire intensity, and smoke exposure. The deterministic model settings include dimensions, seed, wind direction/speed, humidity, and spread scale. The simulation snapshot carries cell values, tick, wind, and humidity.

## Routing and missions

Route graph nodes identify communities, junctions, and shelters. Edges define distance, travel speed, capacity, exposure, closure, mode, and accessibility. Mission records store mission ID, type, target, resource type, lifecycle state, progress, and timestamps. Resources are aggregate counts in this increment.

## SQLite

- `schema_version`: initial schema number.
- `operational_events`: timestamped category, message, and simulation tick.
- `simulation_snapshots`: timestamp, scenario ID, tick, and JSON payload.

Database writes are asynchronous and bounded. These tables are a starting schema, not a complete incident-management database.
