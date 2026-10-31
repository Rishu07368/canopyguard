# Safety, governance, and limitations

## Intended use and non-use

CANOPYGUARD is an educational and operational-planning prototype. It is **not** a certified emergency-warning system, validated wildfire forecast, evacuation-order authority, public notification service, dispatch platform, or replacement for local emergency procedures. It does not independently issue orders or contact the public. All estimates require review by appropriately authorized, trained people.

The fire and smoke algorithms are simplified heuristics and have not been calibrated or scientifically validated. Fire spread and smoke are not predictions; estimated routes do not guarantee safety. Model uncertainty, incomplete inputs, local terrain, weather, road conditions, and changing hazards can make displayed estimates wrong. Confirm conditions through authoritative sources and field procedures.

The emergency workflow in this increment is deliberately limited: the graph router filters modeled constraints and produces candidates; the UI labels them as planning estimates and requires field verification. Mission inventory and progress are local simulation state, not real dispatch. A blocked candidate set must be reported as unavailable; operators must not treat a software suggestion as clearance.

## Community governance and privacy

All included communities and names are fictional. Population estimates are aggregated, not individual records. A real deployment would require meaningful community participation, consent, locally defined governance and data access, appropriate language and accessibility support, liaison involvement, and jurisdictional approval. Traditional knowledge is not assumed to be public or freely reusable. Do not enter sacred or sensitive locations by default. Restrict or spatially generalize sensitive features before sharing or export.

Evacuation recommendations must remain distinguishable from decisions by authorized people. Community-approved arrangements, preferred shelters, communication practices, and local authority must govern any real use. This prototype does not implement jurisdiction-configurable official evacuation states or record an approval workflow.

## Wildlife and habitat

Habitat connectivity and safe natural movement should take precedence over capture/relocation. The current increment displays generalized, fictional habitat features but does **not** implement species-aware movement, refuge suitability, veterinary case management, habitat recovery, or professional wildlife dispatch. It must not be used to direct animal handling. Any real intervention belongs to qualified wildlife professionals and must not be sent into unsafe zones. Burned habitat is not presumed suitable for release.

## Data provenance

The app's only initial source is a bundled synthetic training scenario. No live observations or weather are represented. Imported or future external data will require explicit provenance, observation and retrieval times, quality/confidence, and source category before it can be presented as operational information.

## Before any real deployment

A real system requires validated environmental models and input requirements, authoritative geographic and weather data, trained operators, local community participation and consent, privacy/security controls, jurisdictional evacuation procedures, qualified wildlife experts, accessibility review, and formal approval from responsible emergency-management authorities. It also needs operational testing, failure-mode analysis, audit controls, and independent validation. None of those qualifications is conferred by this prototype.
