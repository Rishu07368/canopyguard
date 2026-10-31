# Test plan

Run `.\scripts\test.ps1` or `mvn test`.

Current JUnit coverage validates seeded fire repeatability and wind effects, burned/fuel-free state behavior, GeoJSON validity and malformed input rejection, route closure/risk/capacity/accessibility handling, alternative paths, contended mission reservations and release, JSON checkpoint restore and invalid schema rejection, and SQLite event persistence.

Additional required verification before operational use includes formal model validation, scenario/property-based testing, threat and smoke calibration, concurrent shutdown and queue saturation tests, database migration/rollback tests, import provenance/size limits, accessibility and UI thread tests, end-to-end UI scenario tests, and Windows package smoke testing on a clean machine.
