package com.canopyguard.app;

import com.canopyguard.domain.*;
import com.canopyguard.engine.WildfireEngine;
import com.canopyguard.geo.*;
import com.canopyguard.persistence.DatabaseService;
import com.canopyguard.persistence.SessionStore;
import com.canopyguard.ui.CanopyGuardShell;
import com.canopyguard.ui.MapCanvas;
import com.canopyguard.ui.UiComponents;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.locationtech.jts.geom.Coordinate;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public final class CanopyGuardApp extends Application {
    private static final String SCENARIO_ID = "emerald-reach";
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private final ScheduledExecutorService simulationExecutor = Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "canopyguard-simulation"));
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(r -> daemon(r, "canopyguard-io"));
    private final SessionStore sessionStore = new SessionStore();
    private final MissionService missions = new MissionService();
    private final Deque<IncidentEvent> events = new ArrayDeque<>();
    private final EnumMap<MapCanvas.Layer, Boolean> layerVisibility = new EnumMap<>(MapCanvas.Layer.class);

    private EmeraldReachScenario scenario;
    private volatile WildfireEngine engine;
    private DatabaseService database;
    private MapCanvas map;
    private StackPane mapFrame;
    private CanopyGuardShell shell;
    private Path dataDirectory;
    private String selectedModule = "Overview";
    private GeographicFeature selectedFeature;
    private ScheduledFuture<?> runTask;
    private Runnable moduleRefresh = () -> {};
    private boolean running;
    private boolean closing;
    private boolean shutdownComplete;
    private int windShiftCount;
    private Label tickLabel;
    private Label[] overviewValues;
    private Label fireConditions;
    private Button simulationToggle;
    private VBox currentTimeline;
    private VBox routeResults;
    private Community chosenCommunity;
    private VBox missionsList;

    @Override public void start(Stage stage) throws Exception {
        scenario = EmeraldReachScenario.load();
        scenario.shelters().forEach(s -> missions.registerShelter(s.id(), s.capacity(), s.occupancy()));
        for (MapCanvas.Layer layer : MapCanvas.Layer.values()) layerVisibility.put(layer, true);
        engine = new WildfireEngine(new WildfireEngine.Settings(26, 22, 20261031L, 28, 17, 39, 0.92), 9, 5);
        dataDirectory = Path.of(System.getenv().getOrDefault("APPDATA", System.getProperty("user.home")), "CanopyGuard", "data");
        try {
            database = new DatabaseService(dataDirectory.resolve("canopyguard.db"));
        } catch (SQLException | IOException e) {
            System.err.println("CANOPYGUARD database unavailable: " + e.getMessage());
        }

        map = new MapCanvas(scenario, 950, 500);
        map.setSelectionHandler(this::handleMapSelection);
        stage.initStyle(StageStyle.DECORATED);
        stage.setResizable(true);
        stage.setTitle("CANOPYGUARD | Rainforest Wildfire Intelligence");
        stage.setMinWidth(900);
        stage.setMinHeight(460);
        shell = new CanopyGuardShell(this::showModule);
        shell.setStatus(database == null ? "PERSISTENCE DEGRADED" : "MONITORING", database == null);
        shell.setModuleContent(buildModule(selectedModule));
        Scene scene = new Scene(shell);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/styles/app-theme.css")).toExternalForm());
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> {
            if (!shutdownComplete) {
                event.consume();
                requestShutdown(stage);
            }
        });

        var bounds = Screen.getPrimary().getVisualBounds();
        stage.setWidth(Math.min(1440, Math.max(stage.getMinWidth(), bounds.getWidth() - 70)));
        stage.setHeight(Math.min(900, Math.max(stage.getMinHeight(), bounds.getHeight() - 70)));
        stage.centerOnScreen();
        stage.show();
        refreshState();
        addEvent("SYSTEM", "Emerald Reach loaded. Simulation mode — synthetic data.", 0);
    }

    private void showModule(String module) {
        if (closing) return;
        selectedModule = module;
        shell.select(module);
        shell.setModuleContent(new Pane());
        simulationToggle = null;
        shell.setModuleContent(buildModule(module));
        refreshState();
    }

    private Node buildModule(String module) {
        return switch (module) {
            case "Geographic intelligence" -> geographicPage();
            case "Fire intelligence" -> firePage();
            case "Community protection" -> communityPage();
            case "Evacuation planning" -> evacuationPage();
            case "Wildlife & habitat" -> habitatPage();
            case "Emergency resources" -> resourcesPage();
            case "Incident history" -> historyPage();
            case "Reports" -> reportsPage();
            case "Settings" -> settingsPage();
            default -> overviewPage();
        };
    }

    private Node overviewPage() {
        Label scenarioCaption = new Label("DRY-SEASON WILDFIRE  /  FICTIONAL RAINFOREST SCENARIO");
        scenarioCaption.getStyleClass().add("eyebrow");
        HBox heading = pageHeading("Emerald Reach", "Operational overview · planning estimates only");
        tickLabel = new Label();
        tickLabel.getStyleClass().add("monospace-value");
        Region push = new Region();
        HBox.setHgrow(push, Priority.ALWAYS);
        heading.getChildren().addAll(push, tickLabel);

        overviewValues = new Label[4];
        HBox metrics = new HBox(0);
        metrics.getStyleClass().add("metric-strip");
        String[] labels = {"BURNING CELLS", "COMMUNITIES ELEVATED", "BURNED CELLS", "SHELTER SPACES"};
        String[] tones = {"tone-fire", "tone-amber", "tone-muted", "tone-green"};
        for (int i = 0; i < labels.length; i++) {
            VBox block = UiComponents.metric(labels[i], "—", "Current synthetic scenario", tones[i]);
            overviewValues[i] = (Label) block.getChildren().get(1);
            HBox.setHgrow(block, Priority.ALWAYS);
            metrics.getChildren().add(block);
        }

        simulationToggle = UiComponents.button(running ? "Pause progression" : "Start progression",
                UiComponents.Tone.PRIMARY, this::toggleSimulation);
        HBox actions = new HBox(9,
                simulationToggle,
                UiComponents.button("Advance one step", UiComponents.Tone.SECONDARY, this::advanceSimulation),
                UiComponents.button("Save checkpoint", UiComponents.Tone.SECONDARY, this::saveCheckpoint));
        actions.setAlignment(Pos.CENTER_LEFT);
        VBox mapPanel = overviewMapPanel();
        VBox column = new VBox(16, scenarioCaption, heading, metrics, actions, mapPanel, compactTimeline());
        column.getStyleClass().add("module-page");
        column.setPadding(new Insets(22, 26, 24, 26));
        ScrollPane scroll = scrollable(column);
        moduleRefresh = () -> {
            WildfireEngine.Snapshot snapshot = engine.snapshot();
            tickLabel.setText("TICK " + String.format(Locale.ROOT, "%03d", snapshot.tick()));
            overviewValues[0].setText(Long.toString(snapshot.burningCount()));
            overviewValues[1].setText(Long.toString(scenario.communities().stream().filter(c -> threatLevel(c, snapshot) >= 2).count()));
            overviewValues[2].setText(Long.toString(snapshot.burnedCount()));
            overviewValues[3].setText(Integer.toString(scenario.shelters().stream().mapToInt(s -> missions.shelterAvailable(s.id())).sum()));
            refreshTimeline();
        };
        return scroll;
    }

    private VBox overviewMapPanel() {
        return UiComponents.panel("Geographic intelligence", "Emerald Reach · WGS 84 · fictional, non-navigational scenario",
                layerControls(), mapLegend(), mapWithDetails(400));
    }

    private Node geographicPage() {
        VBox page = UiComponents.page("Geographic intelligence",
                "Coordinate-based scenario features. Drag to pan; scroll over the map to zoom. Scroll outside it to move this page.",
                layerControls(), mapLegend(), mapWithDetails(550));
        moduleRefresh = this::refreshMapAndDetails;
        return scrollable(page);
    }

    private Node firePage() {
        WildfireEngine.Snapshot initial = engine.snapshot();
        Label estimate = new Label("MODEL ESTIMATE · NOT A VALIDATED FORECAST");
        estimate.getStyleClass().add("warning-callout");
        simulationToggle = UiComponents.button(running ? "Pause progression" : "Start progression",
                UiComponents.Tone.PRIMARY, this::toggleSimulation);
        HBox controls = new HBox(9,
                simulationToggle,
                UiComponents.button("Advance one step", UiComponents.Tone.SECONDARY, this::advanceSimulation),
                UiComponents.button("Apply wind shift", UiComponents.Tone.SECONDARY, this::shiftWind));
        fireConditions = new Label();
        fireConditions.getStyleClass().add("data-copy");
        fireConditions.setWrapText(true);
        VBox conditions = UiComponents.panel("Scenario conditions", "Seeded synthetic inputs", fireConditions,
                new Label("Spread heuristic: neighboring fire × fuel × dryness × wind alignment × slope. Smoke decays and is deposited downwind. Fixed seed: 20261031."));
        conditions.getStyleClass().add("surface-panel");
        HBox mapActions = new HBox(10,
                UiComponents.button("Save checkpoint", UiComponents.Tone.SECONDARY, this::saveCheckpoint),
                UiComponents.button("Restore checkpoint", UiComponents.Tone.SECONDARY, this::restoreCheckpoint));
        VBox page = UiComponents.page("Fire intelligence", "Cell-based fire progression and approximate smoke dispersion.", estimate,
                controls, mapLegend(), mapWithDetails(500), conditions, mapActions);
        moduleRefresh = () -> {
            WildfireEngine.Snapshot snapshot = engine.snapshot();
            fireConditions.setText(String.format(Locale.ROOT,
                    "Scenario tick: %d%nWind: %.0f km/h from %03.0f°%nRelative humidity: %.0f%%%nBurning cells: %d · burned cells: %d%nSource: synthetic, seeded planning heuristic",
                    snapshot.tick(), snapshot.windKph(), snapshot.windDegrees(), snapshot.humidityPercent(),
                    snapshot.burningCount(), snapshot.burnedCount()));
        };
        moduleRefresh.run();
        return scrollable(page);
    }

    private Node communityPage() {
        VBox list = new VBox(8);
        Label boundary = new Label("Aggregated fictional community records only · no resident-level personal data.");
        boundary.getStyleClass().add("information-callout");
        Button analyze = UiComponents.button("Analyze route options", UiComponents.Tone.SECONDARY,
                () -> analyzeRoutes(chosenCommunity == null ? scenario.communities().get(0) : chosenCommunity));
        Button assign = UiComponents.button("Assign evacuation support", UiComponents.Tone.PRIMARY,
                () -> dispatchMission(chosenCommunity == null ? scenario.communities().get(0) : chosenCommunity));
        VBox actions = new VBox(9, analyze, assign);
        VBox panel = UiComponents.panel("Community status", "Modeled exposure is an estimate, not an official evacuation status.", boundary, list, actions);
        VBox page = UiComponents.page("Community protection",
                "Monitor settlement exposure and support authorized, community-governed decisions.", panel);
        moduleRefresh = () -> refreshCommunityRows(list);
        moduleRefresh.run();
        return scrollable(page);
    }

    private Node evacuationPage() {
        ComboBox<Community> selector = new ComboBox<>(FXCollections.observableArrayList(scenario.communities()));
        selector.setMaxWidth(360);
        selector.setValue(scenario.communities().get(0));
        selector.setCellFactory(view -> new ListCell<>() {
            @Override protected void updateItem(Community item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name() + " · population estimate " + item.population());
            }
        });
        selector.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(Community item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name());
            }
        });
        chosenCommunity = selector.getValue();
        selector.setOnAction(event -> { chosenCommunity = selector.getValue(); showRoutes(chosenCommunity); });
        routeResults = new VBox(9);
        Button analyze = UiComponents.button("Calculate route candidates", UiComponents.Tone.PRIMARY, () -> showRoutes(chosenCommunity));
        Button assign = UiComponents.button("Assign simulated evacuation mission", UiComponents.Tone.SECONDARY,
                () -> dispatchMission(chosenCommunity));
        Label caveat = new Label("A candidate is not a safety guarantee. Verify route conditions in the field. This prototype does not issue orders.");
        caveat.getStyleClass().add("warning-callout");
        VBox panel = UiComponents.panel("Route assessment", "Distance is not the only cost; closed, capacity-limited, high-fire-risk, high-smoke-risk, or inaccessible edges are excluded.",
                selector, new HBox(9, analyze, assign), routeResults, caveat, mapLegend(), mapWithDetails(390));
        VBox page = UiComponents.page("Evacuation planning",
                "Risk- and capacity-constrained graph routing to scenario shelters.", panel);
        moduleRefresh = () -> map.setFireSnapshot(engine.snapshot());
        moduleRefresh.run();
        showRoutes(chosenCommunity);
        return scrollable(page);
    }

    private Node habitatPage() {
        VBox habitatRows = new VBox(8);
        HBox tools = new HBox(9,
                UiComponents.button("Inspect habitat on map", UiComponents.Tone.SECONDARY, () -> {
                    GeographicFeature habitat = scenario.features().stream().filter(f -> f.kind().equals("habitat")).findFirst().orElse(null);
                    if (habitat != null) map.setSelectedFeature(habitat);
                }));
        Label scope = new Label("Habitat polygons and the canopy corridor are generalized fictional features. Threat is estimated from nearby fire and smoke cells. This increment does not implement species movement, refugia suitability, wildlife casework, or rescue dispatch.");
        scope.getStyleClass().add("information-callout");
        VBox panel = UiComponents.panel("Habitat exposure", "Connectivity and safe natural movement come before intervention.", habitatRows, tools, scope);
        VBox page = UiComponents.page("Wildlife & habitat",
                "Generalized ecological features; do not use this prototype to direct wildlife handling.", mapLegend(), mapWithDetails(380), panel);
        moduleRefresh = () -> refreshHabitatRows(habitatRows);
        moduleRefresh.run();
        return scrollable(page);
    }

    private Node resourcesPage() {
        missionsList = new VBox(8);
        VBox inventory = new VBox(7);
        VBox panel = UiComponents.panel("Evacuation resource inventory",
                "Buses and boats are reserved by community evacuation missions; other unit counts are informational.",
                inventory);
        VBox missionPanel = UiComponents.panel("Mission lifecycle", "Assignments reserve units atomically and release them once on completion.",
                missionsList);
        VBox page = UiComponents.page("Emergency resources",
                "Allocate a bus or boat through Community Protection → Assign evacuation support, or choose a community and assign from Evacuation Planning. This prototype does not simulate fire suppression or reduced burn damage.",
                panel, missionPanel);
        moduleRefresh = () -> {
            inventory.getChildren().setAll(
                    resourceRow("Evacuation buses", missions.available("BUS"), "2 scenario units"),
                    resourceRow("River boats", missions.available("BOAT"), "1 scenario unit"),
                    resourceRow("Medical teams", missions.available("MEDICAL_TEAM"), "1 scenario unit"),
                    resourceRow("Wildlife teams", missions.available("WILDLIFE_TEAM"), "1 scenario unit"));
            refreshMissionRows();
        };
        moduleRefresh.run();
        return scrollable(page);
    }

    private Node historyPage() {
        VBox feed = new VBox(0);
        ScrollPane historyScroll = new ScrollPane(feed);
        historyScroll.setFitToWidth(true);
        historyScroll.setPrefViewportHeight(560);
        historyScroll.getStyleClass().add("history-scroll");
        VBox panel = UiComponents.panel("Operational timeline", "Newest first · important local scenario actions",
                historyScroll);
        VBox page = UiComponents.page("Incident history",
                "Event records are also written asynchronously to the local SQLite database.", panel);
        currentTimeline = feed;
        moduleRefresh = this::refreshTimeline;
        return scrollable(page);
    }

    private Node reportsPage() {
        Label contents = new Label("Exports include current simulation tick and cell counts, community threat estimates, available shelter capacity, mission status, and timestamped incident events. All synthetic scenario values remain labeled.");
        contents.getStyleClass().add("data-copy");
        contents.setWrapText(true);
        Button csv = UiComponents.button("Export incident CSV", UiComponents.Tone.PRIMARY, () -> exportReport(false));
        Button html = UiComponents.button("Export readable HTML", UiComponents.Tone.SECONDARY, () -> exportReport(true));
        VBox panel = UiComponents.panel("Incident report", "Generated from current application state at export time.",
                contents, new HBox(9, csv, html));
        VBox page = UiComponents.page("Reports",
                "Save an operational summary to a location selected by the operator.", panel);
        return scrollable(page);
    }

    private Node settingsPage() {
        Label data = new Label("User data directory\n" + dataDirectory
                + "\n\nScenario: Emerald Reach (fictional)\nObservation providers: none configured\nMode: offline synthetic simulation\nDatabase: " + (database == null ? "unavailable" : "SQLite / JDBC")
                + "\nPrism renderer: JavaFX default pipeline (platform-selected)");
        data.getStyleClass().add("data-copy");
        data.setWrapText(true);
        Button open = UiComponents.button("Open user data folder", UiComponents.Tone.SECONDARY, this::openDataDirectory);
        Button save = UiComponents.button("Save recovery checkpoint", UiComponents.Tone.PRIMARY, this::saveCheckpoint);
        VBox panel = UiComponents.panel("Application and data", "Offline mode and local storage details.", data, new HBox(9, open, save));
        Label gpu = new Label("Graphics: JavaFX Prism uses the platform-selected Windows renderer and retains its software fallback. CUDA is not required; no GPU-specific workload is justified by this Canvas map.");
        gpu.getStyleClass().add("information-callout");
        gpu.setWrapText(true);
        VBox page = UiComponents.page("Settings",
                "Data provenance, local storage, and application-runtime information.", panel, gpu);
        return scrollable(page);
    }

    private HBox pageHeading(String title, String subtitle) {
        Label heading = new Label(title);
        heading.getStyleClass().add("page-heading");
        Label detail = new Label(subtitle);
        detail.getStyleClass().add("page-description");
        VBox box = new VBox(4, heading, detail);
        HBox row = new HBox(box);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private HBox layerControls() {
        HBox controls = new HBox(8);
        controls.setAlignment(Pos.CENTER_LEFT);
        controls.getStyleClass().add("layer-controls");
        controls.getChildren().add(label("LAYERS", "eyebrow"));
        for (MapCanvas.Layer layer : MapCanvas.Layer.values()) {
            CheckBox check = new CheckBox(layerLabel(layer));
            check.setSelected(layerVisibility.get(layer));
            check.setOnAction(event -> {
                layerVisibility.put(layer, check.isSelected());
                map.setLayerVisible(layer, check.isSelected());
            });
            controls.getChildren().add(check);
        }
        return controls;
    }

    private HBox mapLegend() {
        HBox legend = new HBox(15);
        legend.getStyleClass().add("map-legend");
        legend.setAlignment(Pos.CENTER_LEFT);
        legend.getChildren().addAll(
                legendEntry("Fire", "legend-fire"), legendEntry("Burned", "legend-burned"),
                legendEntry("Water", "legend-water"), legendEntry("Road", "legend-road"),
                legendEntry("Community", "legend-community"), legendEntry("Habitat", "legend-habitat"),
                legendEntry("Corridor", "legend-corridor"), legendEntry("Smoke", "legend-smoke"));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button zoomIn = UiComponents.button("Zoom in", UiComponents.Tone.SECONDARY, map::zoomIn);
        zoomIn.getStyleClass().add("compact-button");
        Button zoomOut = UiComponents.button("Zoom out", UiComponents.Tone.SECONDARY, map::zoomOut);
        zoomOut.getStyleClass().add("compact-button");
        Button reset = UiComponents.button("Reset view", UiComponents.Tone.SECONDARY, map::resetView);
        reset.getStyleClass().add("compact-button");
        legend.getChildren().addAll(spacer, zoomIn, zoomOut, reset);
        return legend;
    }

    private StackPane mapFrame(int height) {
        if (mapFrame == null) {
            map.setManaged(false);
            mapFrame = new StackPane(map);
            mapFrame.getStyleClass().add("map-frame");
            mapFrame.setMinWidth(0);
            mapFrame.setPrefWidth(760);
            mapFrame.setMaxWidth(Double.MAX_VALUE);
            mapFrame.layoutBoundsProperty().addListener((observable, oldBounds, newBounds) ->
                    map.resizeMap(newBounds.getWidth(), newBounds.getHeight()));
        }
        mapFrame.setMinHeight(Math.min(280, height));
        mapFrame.setPrefHeight(height);
        mapFrame.setMaxHeight(height);
        return mapFrame;
    }

    private HBox mapWithDetails(double mapHeight) {
        VBox featureDetails = new VBox(8);
        featureDetails.getStyleClass().add("map-inspector");
        featureDetails.setPadding(new Insets(14));
        Label title = new Label("FEATURE INSPECTOR");
        title.getStyleClass().add("eyebrow");
        Label details = new Label(selectedFeature == null
                ? "Select a community, shelter, habitat, road, river, or fire feature."
                : describe(selectedFeature));
        details.setWrapText(true);
        details.getStyleClass().add("data-copy");
        featureDetails.getChildren().addAll(title, details);
        HBox view = new HBox(0, mapFrame((int) mapHeight), featureDetails);
        view.getStyleClass().add("map-layout");
        HBox.setHgrow(view.getChildren().get(0), Priority.ALWAYS);
        view.setMinHeight(mapHeight);
        view.setPrefHeight(mapHeight);
        view.setMaxHeight(mapHeight);
        featureDetails.setPrefWidth(230);
        featureDetails.setMinWidth(190);
        featureDetails.setMaxWidth(280);
        map.setSelectionHandler(feature -> {
            handleMapSelection(feature);
            details.setText(feature == null
                    ? "Select a community, shelter, habitat, road, river, or fire feature."
                    : describe(feature));
        });
        return view;
    }

    private void handleMapSelection(GeographicFeature feature) {
        selectedFeature = feature;
        if (feature != null) addEvent("MAP", "Selected " + feature.name() + " (" + feature.kind() + ")", engine.snapshot().tick());
        if (selectedModule.equals("Community protection") && feature != null && feature.kind().equals("community"))
            chosenCommunity = communityFor(feature);
    }

    private void refreshMapAndDetails() {
        map.setFireSnapshot(engine.snapshot());
        map.setLayerVisible(MapCanvas.Layer.SMOKE, layerVisibility.get(MapCanvas.Layer.SMOKE));
        map.setLayerVisible(MapCanvas.Layer.FIRE, layerVisibility.get(MapCanvas.Layer.FIRE));
    }

    private void showRoutes(Community community) {
        if (community == null || routeResults == null) return;
        List<RoutePlanner.Route> routes = routesFor(community);
        routeResults.getChildren().clear();
        map.clearRoute();
        if (routes.isEmpty()) {
            Label none = new Label("No modeled route with current capacity, accessibility, and shelter constraints. No safe route is inferred.");
            none.getStyleClass().add("warning-callout");
            none.setWrapText(true);
            routeResults.getChildren().add(none);
            return;
        }
        for (int i = 0; i < routes.size(); i++) {
            RoutePlanner.Route route = routes.get(i);
            String shelterId = route.nodeIds().get(route.nodeIds().size() - 1);
            String shelterName = scenario.shelters().stream().filter(s -> s.id().equals(shelterId))
                    .map(Shelter::name).findFirst().orElse("Scenario facility");
            Label description = new Label((i == 0 ? "PRIMARY" : "ALTERNATIVE " + i) + " · " + shelterName
                    + " · " + Math.round(route.estimatedMinutes()) + " min · modeled exposure " + Math.round(route.maxRisk() * 100)
                    + "% · capacity checked for " + community.population() + "\n" + String.join("  →  ", route.nodeIds()));
            description.getStyleClass().add("route-row");
            description.setWrapText(true);
            routeResults.getChildren().add(description);
            if (i == 0) map.setRoute(route.nodeIds().stream().map(this::routeCoordinate).toList());
        }
        addEvent("ROUTE", "Calculated " + routes.size() + " candidates for " + community.name(), engine.snapshot().tick());
    }

    private void refreshCommunityRows(VBox list) {
        WildfireEngine.Snapshot snapshot = engine.snapshot();
        list.getChildren().clear();
        for (Community community : scenario.communities()) {
            int threat = threatLevel(community, snapshot);
            Label info = new Label(community.name() + "    " + community.population() + " estimated residents\n"
                    + "Scenario status: " + community.evacuationStatus() + "    ·    modeled exposure: " + threatName(threat)
                    + (community.accessibilitySupport() ? "    ·    accessibility support noted" : ""));
            info.getStyleClass().add("community-row");
            info.setWrapText(true);
            Button inspect = UiComponents.button("Locate", UiComponents.Tone.SECONDARY, () -> locateCommunity(community));
            Button route = UiComponents.button("Routes", UiComponents.Tone.SECONDARY, () -> {
                chosenCommunity = community;
                showModule("Evacuation planning");
                chosenCommunity = community;
                showRoutes(community);
            });
            HBox actions = new HBox(7, inspect, route);
            VBox row = new VBox(8, info, actions);
            row.getStyleClass().add("list-row");
            list.getChildren().add(row);
        }
    }

    private void refreshHabitatRows(VBox list) {
        list.getChildren().clear();
        WildfireEngine.Snapshot snapshot = engine.snapshot();
        scenario.features().stream().filter(feature -> feature.kind().equals("habitat") || feature.kind().equals("corridor"))
                .forEach(feature -> {
                    int threat = threatAt(feature.geometry().getCentroid().getX(), feature.geometry().getCentroid().getY(), snapshot);
                    Label row = new Label(feature.name() + "    ·    " + feature.kind()
                            + "    ·    modeled nearby exposure: " + threatName(threat)
                            + "\nLocation generalized; feature and fire layers are selectable on the map.");
                    row.setWrapText(true);
                    row.getStyleClass().add("community-row");
                    Button locate = UiComponents.button("Inspect map feature", UiComponents.Tone.SECONDARY,
                            () -> map.setSelectedFeature(feature));
                    HBox line = new HBox(12, row, locate);
                    line.setAlignment(Pos.CENTER_LEFT);
                    HBox.setHgrow(row, Priority.ALWAYS);
                    list.getChildren().add(line);
                });
    }

    private void refreshMissionRows() {
        missionsList.getChildren().clear();
        List<MissionService.Mission> records = missions.missions();
        if (records.isEmpty()) {
            missionsList.getChildren().add(emptyState("No missions assigned", "Use a community's route review to assign a simulated support mission."));
            return;
        }
        for (MissionService.Mission mission : records) {
            Label info = new Label(mission.type() + "    ·    " + mission.target() + "\n"
                    + mission.status() + "    ·    " + mission.progressTicks() + "%    ·    " + mission.resourceType()
                    + (mission.shelterId() == null ? "" : "    ·    shelter " + mission.shelterId()));
            info.getStyleClass().add("community-row");
            info.setWrapText(true);
            HBox row = new HBox(9, info);
            if (mission.status() == MissionService.Status.ACTIVE) {
                Button cancel = UiComponents.button("Cancel", UiComponents.Tone.DANGER, () -> confirmCancel(mission));
                cancel.getStyleClass().add("compact-button");
                row.getChildren().add(cancel);
            }
            row.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(info, Priority.ALWAYS);
            missionsList.getChildren().add(row);
        }
    }

    private void refreshTimeline() {
        if (currentTimeline == null) return;
        currentTimeline.getChildren().clear();
        if (events.isEmpty()) {
            currentTimeline.getChildren().add(emptyState("No incident activity yet", "Simulation actions will appear here."));
            return;
        }
        for (IncidentEvent event : events) {
            Label row = new Label(TIME_FORMAT.format(event.occurredAt()) + "    T" + String.format(Locale.ROOT, "%03d", event.tick())
                    + "    " + event.category().toUpperCase(Locale.ROOT) + "    " + event.message());
            row.getStyleClass().add("timeline-entry");
            row.setWrapText(true);
            currentTimeline.getChildren().add(row);
        }
    }

    private VBox compactTimeline() {
        VBox feed = new VBox(6);
        currentTimeline = feed;
        VBox panel = UiComponents.panel("Incident activity", "Chronological events · newest first", feed);
        refreshTimeline();
        return panel;
    }

    private void advanceSimulation() {
        if (closing || simulationExecutor.isShutdown()) return;
        try {
            simulationExecutor.execute(() -> {
                WildfireEngine.Snapshot snapshot = engine.advance();
                Platform.runLater(() -> {
                    if (closing) return;
                    List<MissionService.Mission> changed = missions.advance();
                    refreshState();
                    addEvent("FIRE", "Tick " + snapshot.tick() + " · " + snapshot.burningCount() + " active burning cells.", snapshot.tick());
                    for (MissionService.Mission mission : changed)
                        if (mission.status() == MissionService.Status.COMPLETED)
                            addEvent("MISSION", mission.id() + " completed · " + mission.resourceType() + " released.", snapshot.tick());
                    refreshState();
                });
            });
        } catch (RejectedExecutionException e) {
            if (!closing) setStatus("Simulation worker is unavailable.", true);
        }
    }

    private void toggleSimulation() {
        if (closing) return;
        if (running) {
            running = false;
            if (runTask != null) runTask.cancel(false);
            runTask = null;
            addEvent("SIMULATION", "Progression paused.", engine.snapshot().tick());
        } else {
            running = true;
            runTask = simulationExecutor.scheduleAtFixedRate(this::advanceSimulation, 0, 1600, TimeUnit.MILLISECONDS);
            addEvent("SIMULATION", "Synthetic scenario progression started.", engine.snapshot().tick());
        }
        if (simulationToggle != null)
            simulationToggle.setText(running ? "Pause progression" : "Start progression");
        refreshState();
    }

    private void shiftWind() {
        if (closing) return;
        windShiftCount++;
        double direction = windShiftCount % 2 == 1 ? 228 : 28;
        double speed = windShiftCount % 2 == 1 ? 31 : 17;
        engine.changeWind(direction, speed);
        addEvent("WEATHER", "Synthetic wind changed to " + (int) direction + "° at " + (int) speed + " km/h.", engine.snapshot().tick());
        refreshState();
    }

    private void saveCheckpoint() {
        if (closing) return;
        WildfireEngine.Snapshot snapshot = engine.snapshot();
        Path target = dataDirectory.resolve("saves").resolve("last-session.json");
        CompletableFuture.supplyAsync(() -> saveSnapshot(target, snapshot), ioExecutor)
                .thenCompose(payload -> database == null ? CompletableFuture.<Void>completedFuture(null)
                        : database.recordSnapshot(SCENARIO_ID, snapshot.tick(), payload))
                .whenComplete((ignored, error) -> Platform.runLater(() -> {
                    if (closing) return;
                    if (error != null) setStatus("CHECKPOINT FAILED", true);
                    else {
                        addEvent("PERSISTENCE", "Recovery checkpoint saved.", snapshot.tick());
                        setStatus("CHECKPOINT SAVED", false);
                    }
                }));
    }

    private String saveSnapshot(Path target, WildfireEngine.Snapshot snapshot) {
        try {
            sessionStore.save(target, snapshot);
            return sessionStore.toJson(snapshot);
        } catch (IOException e) {
            throw new CompletionException(e);
        }
    }

    private void restoreCheckpoint() {
        if (closing) return;
        Path target = dataDirectory.resolve("saves").resolve("last-session.json");
        CompletableFuture.supplyAsync(() -> {
            try {
                WildfireEngine.Snapshot snapshot = sessionStore.load(target, 26, 22);
                return WildfireEngine.restore(new WildfireEngine.Settings(26, 22, 20261031L, 28, 17, 39, 0.92), snapshot);
            } catch (IOException | IllegalArgumentException e) {
                throw new CompletionException(e);
            }
        }, ioExecutor).thenAccept(restored -> Platform.runLater(() -> {
            if (closing) return;
            engine = restored;
            addEvent("PERSISTENCE", "Restored simulation at tick " + restored.snapshot().tick(), restored.snapshot().tick());
            refreshState();
            setStatus("CHECKPOINT RESTORED", false);
        })).exceptionally(error -> {
            Platform.runLater(() -> { if (!closing) setStatus("RESTORE FAILED · " + rootMessage(error), true); });
            return null;
        });
    }

    private void dispatchMission(Community target) {
        if (closing || target == null) return;
        if (threatLevel(target, engine.snapshot()) >= 3) {
            setStatus("HIGH EXPOSURE · HUMAN ASSESSMENT REQUIRED", true);
            return;
        }
        try {
            RoutePlanner.Route route = routesFor(target).stream().findFirst()
                    .orElseThrow(() -> new IllegalStateException("No modeled route with spare capacity"));
            if (route.maxRisk() >= 0.75) throw new IllegalStateException("Candidate exposure is too high for simulated assignment");
            String shelterId = route.nodeIds().get(route.nodeIds().size() - 1);
            String resource = target.id().equals("COMM-02") ? "BOAT" : "BUS";
            MissionService.Mission mission = missions.dispatchEvacuation(target.name(), resource, shelterId, target.population());
            addEvent("MISSION", "Simulated mission " + mission.id() + " reserved " + resource
                    + " and shelter space for " + mission.evacuees() + ".", engine.snapshot().tick());
            setStatus("SIMULATED MISSION RESERVED · NO ORDER ISSUED", false);
            refreshState();
        } catch (IllegalStateException e) {
            setStatus("MISSION NOT ASSIGNED · " + e.getMessage(), true);
        }
    }

    private void analyzeRoutes(Community community) {
        chosenCommunity = community;
        showModule("Evacuation planning");
    }

    private List<RoutePlanner.Route> routesFor(Community community) {
        if (community == null) return List.of();
        return scenario.shelters().stream().filter(s -> missions.shelterAvailable(s.id()) >= community.population())
                .flatMap(s -> scenario.routes().alternatives(community.id(), s.id(), community.population(),
                        community.accessibilitySupport(), 2).stream())
                .sorted(Comparator.comparingDouble(RoutePlanner.Route::estimatedMinutes))
                .limit(3).toList();
    }

    private void addEvent(String category, String message, int tick) {
        IncidentEvent event = new IncidentEvent(Instant.now(), tick, category, message);
        events.addFirst(event);
        while (events.size() > 500) events.removeLast();
        refreshTimeline();
        if (database != null && !closing) database.recordEvent(category, message, tick).exceptionally(error -> {
            Platform.runLater(() -> { if (!closing) setStatus("HISTORY WRITE FAILED", true); });
            return null;
        });
    }

    private void exportReport(boolean html) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export CANOPYGUARD incident report");
        chooser.getExtensionFilters().add(html
                ? new FileChooser.ExtensionFilter("HTML report", "*.html")
                : new FileChooser.ExtensionFilter("CSV report", "*.csv"));
        chooser.setInitialFileName("emerald-reach-situation-report." + (html ? "html" : "csv"));
        Path target;
        try {
            var selected = chooser.showSaveDialog(shell.getScene().getWindow());
            if (selected == null) return;
            target = selected.toPath();
        } catch (RuntimeException e) {
            setStatus("REPORT DIALOG FAILED · " + e.getMessage(), true);
            return;
        }
        WildfireEngine.Snapshot snapshot = engine.snapshot();
        List<IncidentEvent> history = List.copyOf(events);
        CompletableFuture.runAsync(() -> {
            try {
                Files.writeString(target, html ? htmlReport(snapshot, history) : csvReport(snapshot, history),
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, ioExecutor).whenComplete((ignored, error) -> Platform.runLater(() -> {
            if (closing) return;
            if (error != null) setStatus("REPORT EXPORT FAILED · " + rootMessage(error), true);
            else {
                addEvent("REPORT", "Exported " + target.getFileName(), snapshot.tick());
                setStatus("REPORT EXPORTED", false);
            }
        }));
    }

    private String csvReport(WildfireEngine.Snapshot snapshot, List<IncidentEvent> history) {
        StringBuilder csv = new StringBuilder("record_type,scenario,tick,time,category,detail,value\n");
        csv.append(csv("summary")).append(',').append(csv("Emerald Reach")).append(',').append(snapshot.tick()).append(',')
                .append(csv(TIME_FORMAT.format(Instant.now()))).append(',').append(csv("simulation"))
                .append(',').append(csv("synthetic planning estimate"))
                .append(',').append(csv("burning cells=" + snapshot.burningCount() + "; burned cells=" + snapshot.burnedCount())).append('\n');
        for (Community community : scenario.communities())
            csv.append(csv("community")).append(',').append(csv("Emerald Reach")).append(',').append(snapshot.tick())
                    .append(",,").append(csv(community.id())).append(',').append(csv(community.name())).append(',')
                    .append(csv("population estimate=" + community.population() + "; modeled exposure="
                            + threatName(threatLevel(community, snapshot)))).append('\n');
        for (IncidentEvent event : history)
            csv.append(csv("event")).append(',').append(csv("Emerald Reach")).append(',').append(event.tick()).append(',')
                    .append(csv(event.occurredAt().toString())).append(',').append(csv(event.category()))
                    .append(',').append(csv(event.message())).append(',').append(csv("")).append('\n');
        return csv.toString();
    }

    private String htmlReport(WildfireEngine.Snapshot snapshot, List<IncidentEvent> history) {
        StringBuilder html = new StringBuilder("""
                <!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width">
                <title>Emerald Reach situation report</title>
                <style>body{font:15px 'Segoe UI',sans-serif;max-width:980px;margin:40px auto;padding:0 20px;color:#24342b}
                h1,h2{color:#294731}table{border-collapse:collapse;width:100%}td,th{padding:9px;border-bottom:1px solid #d5ded2;text-align:left}
                .notice{background:#f3ead0;padding:14px}code{font-family:Consolas,monospace}</style><body>
                <h1>Emerald Reach · Situation report</h1>
                <p class="notice"><strong>SIMULATION MODE — SYNTHETIC DATA.</strong> Fictional planning scenario; not a validated forecast, evacuation order, or dispatch instruction.</p>
                <h2>Fire scenario</h2><p>Tick: """).append(snapshot.tick()).append(" · Burning cells: ")
                .append(snapshot.burningCount()).append(" · Burned cells: ").append(snapshot.burnedCount())
                .append("</p><h2>Communities</h2><table><tr><th>Community</th><th>Population estimate</th><th>Modeled exposure</th></tr>");
        for (Community community : scenario.communities()) html.append("<tr><td>").append(escape(community.name()))
                .append("</td><td>").append(community.population()).append("</td><td>")
                .append(escape(threatName(threatLevel(community, snapshot)))).append("</td></tr>");
        html.append("</table><h2>Incident history</h2><table><tr><th>Time</th><th>Tick</th><th>Category</th><th>Event</th></tr>");
        for (IncidentEvent event : history) html.append("<tr><td>").append(escape(event.occurredAt().toString()))
                .append("</td><td>").append(event.tick()).append("</td><td>").append(escape(event.category()))
                .append("</td><td>").append(escape(event.message())).append("</td></tr>");
        return html.append("</table></body></html>").toString();
    }

    private String csv(String value) { return "\"" + value.replace("\"", "\"\"") + "\""; }
    private String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private void openDataDirectory() {
        CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(dataDirectory);
                if (!Desktop.isDesktopSupported()) throw new IOException("Desktop folder navigation is unavailable on this system");
                Desktop.getDesktop().open(dataDirectory.toFile());
            } catch (IOException | UnsupportedOperationException e) {
                throw new CompletionException(e);
            }
        }, ioExecutor).whenComplete((ignored, error) -> Platform.runLater(() -> {
            if (closing) return;
            setStatus(error == null ? "DATA FOLDER OPENED" : "FOLDER OPEN FAILED · " + rootMessage(error), error != null);
        }));
    }

    private void confirmCancel(MissionService.Mission mission) {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Cancel simulated mission");
        confirmation.setHeaderText("Release reserved resources?");
        confirmation.setContentText("Cancel " + mission.id() + " for " + mission.target()
                + "? Its simulated vehicle and reserved shelter capacity will be released.");
        confirmation.initOwner(shell.getScene().getWindow());
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            missions.cancel(mission.id());
            addEvent("MISSION", "Cancelled " + mission.id() + "; vehicle and shelter reservation released.", engine.snapshot().tick());
            refreshState();
        }
    }

    private void locateCommunity(Community community) {
        GeographicFeature feature = scenario.features().stream().filter(candidate -> candidate.kind().equals("community"))
                .filter(candidate -> community.id().equals(candidate.properties().get("idref"))).findFirst().orElse(null);
        if (feature != null) {
            map.setSelectedFeature(feature);
            showModule("Geographic intelligence");
        }
    }

    private Community communityFor(GeographicFeature feature) {
        return scenario.communities().stream().filter(community -> community.id()
                .equals(feature.properties().get("idref"))).findFirst().orElse(null);
    }

    private int threatLevel(Community community, WildfireEngine.Snapshot snapshot) {
        return threatAt(community.longitude(), community.latitude(), snapshot);
    }

    private int threatAt(double longitude, double latitude, WildfireEngine.Snapshot snapshot) {
        double minDistance = Double.POSITIVE_INFINITY;
        double nearbySmoke = 0;
        for (FireCell cell : snapshot.cells()) {
            double lon = scenario.minLongitude() + (cell.x() + 0.5) / snapshot.width()
                    * (scenario.maxLongitude() - scenario.minLongitude());
            double lat = scenario.maxLatitude() - (cell.y() + 0.5) / snapshot.height()
                    * (scenario.maxLatitude() - scenario.minLatitude());
            double distance = Math.hypot((lon - longitude) * Math.cos(Math.toRadians(latitude)), lat - latitude);
            if (cell.state() == FireCell.FireState.BURNING || cell.state() == FireCell.FireState.BURNED)
                minDistance = Math.min(minDistance, distance);
            if (distance < 0.09) nearbySmoke = Math.max(nearbySmoke, cell.smoke());
        }
        if (minDistance < 0.035 || nearbySmoke > 0.75) return 3;
        if (minDistance < 0.075 || nearbySmoke > 0.45) return 2;
        if (minDistance < 0.13 || nearbySmoke > 0.22) return 1;
        return 0;
    }

    private String describe(GeographicFeature feature) {
        StringBuilder text = new StringBuilder(feature.kind().toUpperCase(Locale.ROOT)).append("\n").append(feature.name());
        if (feature.kind().equals("community")) {
            Community community = communityFor(feature);
            if (community != null) text.append("\n\nAggregated population estimate: ").append(community.population())
                    .append("\nScenario status: ").append(community.evacuationStatus())
                    .append("\nModeled exposure: ").append(threatName(threatLevel(community, engine.snapshot())))
                    .append("\nNo individual resident records are stored.");
        } else if (feature.kind().equals("shelter")) {
            scenario.shelters().stream().filter(shelter -> shelter.id().equals(feature.properties().get("idref")))
                    .findFirst().ifPresent(shelter -> text.append("\n\nCapacity: ").append(shelter.capacity())
                            .append("\nScenario and reserved occupancy: ").append(missions.shelterOccupancy(shelter.id()))
                            .append("\nUnreserved: ").append(missions.shelterAvailable(shelter.id())));
        } else if (feature.kind().equals("habitat") || feature.kind().equals("corridor")) {
            int threat = threatAt(feature.geometry().getCentroid().getX(), feature.geometry().getCentroid().getY(), engine.snapshot());
            text.append("\n\nGeneralized location. Nearby modeled exposure: ").append(threatName(threat))
                    .append("\nHabitat protection and connectivity first. No wildlife capture or rescue is directed.");
        } else {
            String provenance = feature.properties().get("confidence");
            if (provenance != null) text.append("\n\nScenario provenance: ").append(provenance);
        }
        return text.toString();
    }

    private void refreshState() {
        if (closing || shell == null) return;
        map.setFireSnapshot(engine.snapshot());
        moduleRefresh.run();
        if (database == null) shell.setStatus("PERSISTENCE DEGRADED", true);
        else if (!database.lastError().isBlank()) shell.setStatus("PERSISTENCE DEGRADED", true);
        else shell.setStatus(running ? "SIMULATION RUNNING" : "MONITORING", false);
    }

    private Node resourceRow(String label, int available, String total) {
        HBox row = new HBox(10);
        row.getStyleClass().add("inventory-row");
        row.setAlignment(Pos.CENTER_LEFT);
        Label type = new Label(label);
        type.getStyleClass().add("data-copy");
        Label stock = new Label(available + " available");
        stock.getStyleClass().add("inventory-count");
        Label caption = new Label(total);
        caption.getStyleClass().add("muted-copy");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        row.getChildren().addAll(type, spacer, stock, caption);
        return row;
    }

    private Node emptyState(String title, String description) {
        VBox state = new VBox(4);
        state.getStyleClass().add("empty-state");
        Label heading = new Label(title);
        heading.getStyleClass().add("empty-title");
        Label detail = new Label(description);
        detail.setWrapText(true);
        detail.getStyleClass().add("muted-copy");
        state.getChildren().addAll(heading, detail);
        return state;
    }

    private ScrollPane scrollable(Node content) {
        content.getStyleClass().add("module-content");
        StackPane contentFrame = new StackPane(content);
        contentFrame.setAlignment(Pos.TOP_CENTER);
        ScrollPane scroll = new ScrollPane(contentFrame);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("module-scroll");
        return scroll;
    }

    private Region label(String text, String style) {
        Label label = new Label(text);
        label.getStyleClass().add(style);
        return label;
    }

    private HBox legendEntry(String title, String colorClass) {
        Region swatch = new Region();
        swatch.getStyleClass().addAll("legend-swatch", colorClass);
        Label text = new Label(title);
        text.getStyleClass().add("legend-label");
        return new HBox(6, swatch, text);
    }

    private String layerLabel(MapCanvas.Layer layer) {
        return switch (layer) {
            case FOREST -> "Forest";
            case WATER -> "Water";
            case INFRASTRUCTURE -> "Roads";
            case COMMUNITIES -> "Communities";
            case HABITATS -> "Habitat";
            case FIRE -> "Fire";
            case SMOKE -> "Smoke";
        };
    }

    private String threatName(int value) {
        return switch (value) {
            case 3 -> "HIGH · immediate authorized review";
            case 2 -> "ELEVATED";
            case 1 -> "MONITOR";
            default -> "LOW · no current proximity signal";
        };
    }

    private void setStatus(String text, boolean error) {
        if (shell != null) shell.setStatus(text, error);
    }

    private Coordinate routeCoordinate(String node) {
        return switch (node) {
            case "COMM-01" -> new Coordinate(-61.50, -2.48);
            case "COMM-02" -> new Coordinate(-61.36, -2.58);
            case "COMM-03" -> new Coordinate(-61.26, -2.35);
            case "JCT-01" -> new Coordinate(-61.43, -2.51);
            case "JCT-02" -> new Coordinate(-61.34, -2.39);
            case "SHEL-01" -> new Coordinate(-61.24, -2.55);
            case "SHEL-02" -> new Coordinate(-61.53, -2.68);
            default -> throw new IllegalArgumentException("Unknown route node: " + node);
        };
    }

    private void requestShutdown(Stage stage) {
        if (closing) return;
        closing = true;
        running = false;
        if (runTask != null) runTask.cancel(false);
        shell.showClosing();
        Thread coordinator = daemon(() -> {
            try {
                simulationExecutor.shutdown();
                if (!simulationExecutor.awaitTermination(8, TimeUnit.SECONDS)) {
                    simulationExecutor.shutdownNow();
                    if (!simulationExecutor.awaitTermination(3, TimeUnit.SECONDS))
                        throw new IllegalStateException("Simulation worker did not stop in time");
                }

                ioExecutor.shutdown();
                if (!ioExecutor.awaitTermination(8, TimeUnit.SECONDS)) {
                    ioExecutor.shutdownNow();
                    if (!ioExecutor.awaitTermination(3, TimeUnit.SECONDS))
                        throw new IllegalStateException("File worker did not stop in time");
                }

                WildfireEngine.Snapshot snapshot = engine.snapshot();
                Path checkpoint = dataDirectory.resolve("saves").resolve("last-session.json");
                String payload = saveSnapshot(checkpoint, snapshot);
                if (database != null) {
                    try {
                        CompletableFuture.allOf(
                                database.recordEvent("SYSTEM", "Application closed; recovery checkpoint saved.", snapshot.tick()),
                                database.recordSnapshot(SCENARIO_ID, snapshot.tick(), payload))
                                .get(8, TimeUnit.SECONDS);
                    } catch (ExecutionException | TimeoutException e) {
                        System.err.println("CANOPYGUARD final database checkpoint warning: " + rootMessage(e));
                    }
                    database.close();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("CANOPYGUARD shutdown interrupted.");
            } catch (IllegalStateException e) {
                System.err.println("CANOPYGUARD final checkpoint failed: " + e.getMessage());
            } finally {
                if (database != null) {
                    try { database.close(); }
                    catch (RuntimeException closeError) { System.err.println("CANOPYGUARD database close failed: " + closeError.getMessage()); }
                }
                Platform.runLater(() -> {
                    shutdownComplete = true;
                    stage.close();
                });
            }
        }, "canopyguard-shutdown");
        coordinator.setDaemon(true);
        coordinator.start();
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static Thread daemon(Runnable runnable, String name) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        return thread;
    }

    @Override public void stop() {
        if (!shutdownComplete) {
            simulationExecutor.shutdownNow();
            ioExecutor.shutdownNow();
            if (database != null) database.close();
        }
    }

    private record IncidentEvent(Instant occurredAt, int tick, String category, String message) {}

    public static void main(String[] args) {
        launch(args);
    }
}
