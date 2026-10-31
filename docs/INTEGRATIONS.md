# Integrations

The initial release is fully offline and has no configured external provider. Synthetic scenario observations are labeled in the application and are not represented as live.

Future observation, weather, geographic-data, community-report, and wildlife-sighting connections should be adapters at the application boundary, never network calls from domain entities. Provider records should include source, category, observed-at time, retrieved-at time, quality/confidence, and live/imported/simulated status. Imports should be validated before acceptance and should not block the JavaFX or simulation threads.

No credentials or API keys are shipped. Connectivity failure must leave the bundled scenario usable and explicitly disclose the unavailable provider.
