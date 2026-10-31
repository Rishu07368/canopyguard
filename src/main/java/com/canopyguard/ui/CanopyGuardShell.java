package com.canopyguard.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.control.OverrunStyle;
import javafx.scene.layout.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class CanopyGuardShell extends BorderPane {
    private static final String[] MODULES = {
            "Overview", "Geographic intelligence", "Fire intelligence", "Community protection",
            "Evacuation planning", "Wildlife & habitat", "Emergency resources", "Incident history",
            "Reports", "Settings"
    };
    private final Map<String, Button> navigation = new LinkedHashMap<>();
    private final Label moduleTitle = new Label("Overview");
    private final Label status = new Label("INITIALIZING");
    private final Tooltip statusTooltip = new Tooltip("INITIALIZING");
    private final StackPane workspace = new StackPane();
    private final VBox shutdownOverlay = new VBox(13);

    public CanopyGuardShell(Consumer<String> selectionHandler) {
        getStyleClass().add("app-shell");
        setTop(buildToolbar());
        setLeft(buildSidebar(selectionHandler));
        StackPane content = new StackPane(workspace, shutdownOverlay);
        shutdownOverlay.getStyleClass().add("shutdown-overlay");
        shutdownOverlay.setAlignment(Pos.CENTER);
        shutdownOverlay.setVisible(false);
        shutdownOverlay.setManaged(false);
        Label closingTitle = new Label("Saving incident state");
        closingTitle.getStyleClass().add("shutdown-title");
        Label closingDetail = new Label("Draining simulation and database work before closing.");
        closingDetail.getStyleClass().add("shutdown-copy");
        ProgressIndicator progress = new ProgressIndicator();
        progress.setMaxSize(28, 28);
        shutdownOverlay.getChildren().addAll(progress, closingTitle, closingDetail);
        setCenter(content);
    }

    private HBox buildToolbar() {
        HBox toolbar = new HBox(10);
        toolbar.getStyleClass().add("app-toolbar");
        toolbar.setAlignment(Pos.CENTER_LEFT);
        VBox identity = new VBox(2);
        Label brand = new Label("CANOPYGUARD");
        brand.getStyleClass().add("brand-title");
        Label strap = new Label("WILDFIRE · COMMUNITY · ECOLOGY");
        strap.getStyleClass().add("brand-subtitle");
        identity.getChildren().addAll(brand, strap);
        identity.setMaxWidth(175);
        moduleTitle.getStyleClass().add("toolbar-module");
        moduleTitle.setMaxWidth(150);
        moduleTitle.setTextOverrun(OverrunStyle.ELLIPSIS);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        VBox incident = new VBox(2);
        Label incidentLabel = new Label("ACTIVE SCENARIO");
        incidentLabel.getStyleClass().add("toolbar-kicker");
        Label incidentName = new Label("Emerald Reach  /  Dry-season fire");
        incidentName.getStyleClass().add("toolbar-incident");
        incident.setMaxWidth(175);
        incidentName.setTextOverrun(OverrunStyle.ELLIPSIS);
        incident.getChildren().addAll(incidentLabel, incidentName);
        Label mode = new Label("SIMULATION · SYNTHETIC");
        mode.getStyleClass().add("mode-badge");
        mode.setMaxWidth(130);
        mode.setTextOverrun(OverrunStyle.ELLIPSIS);
        status.getStyleClass().add("status-chip");
        status.setMaxWidth(135);
        status.setTextOverrun(OverrunStyle.ELLIPSIS);
        status.setTooltip(statusTooltip);
        toolbar.getChildren().addAll(identity, moduleTitle, spacer, incident, mode, status);
        return toolbar;
    }

    private VBox buildSidebar(Consumer<String> selectionHandler) {
        VBox sidebar = new VBox(5);
        sidebar.getStyleClass().add("navigation-rail");
        sidebar.setPadding(new Insets(19, 10, 14, 10));
        Label heading = new Label("OPERATIONS");
        heading.getStyleClass().add("navigation-heading");
        sidebar.getChildren().add(heading);
        VBox moduleList = new VBox(4);
        for (String module : MODULES) {
            Button item = new Button(module);
            item.setGraphic(new ModuleGlyph(module));
            item.getStyleClass().add("navigation-item");
            item.setMaxWidth(Double.MAX_VALUE);
            item.setMinHeight(37);
            item.setFocusTraversable(true);
            item.setOnAction(event -> {
                select(module);
                selectionHandler.accept(module);
            });
            navigation.put(module, item);
            moduleList.getChildren().add(item);
        }
        ScrollPane navigationScroll = new ScrollPane(moduleList);
        navigationScroll.setFitToWidth(true);
        navigationScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        navigationScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        navigationScroll.getStyleClass().add("navigation-scroll");
        VBox.setVgrow(navigationScroll, Priority.ALWAYS);
        Label region = new Label("EMERALD REACH\nFictional training region");
        region.getStyleClass().add("navigation-footnote");
        sidebar.getChildren().addAll(navigationScroll, region);
        select(MODULES[0]);
        return sidebar;
    }

    public void select(String module) {
        moduleTitle.setText(module);
        for (Map.Entry<String, Button> entry : navigation.entrySet()) {
            entry.getValue().getStyleClass().remove("navigation-item-selected");
            if (entry.getKey().equals(module)) entry.getValue().getStyleClass().add("navigation-item-selected");
        }
    }

    public void setModuleContent(Node node) {
        workspace.getChildren().setAll(node);
    }

    public void setStatus(String message, boolean error) {
        status.setText(message);
        statusTooltip.setText(message);
        status.getStyleClass().removeAll("status-chip", "status-chip-error", "status-chip-ok");
        status.getStyleClass().add(error ? "status-chip-error" : "status-chip-ok");
    }

    public void showClosing() {
        workspace.setDisable(true);
        shutdownOverlay.setVisible(true);
        shutdownOverlay.setManaged(true);
    }

    public Label moduleTitle() { return moduleTitle; }
}
