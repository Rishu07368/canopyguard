package com.canopyguard.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public final class UiComponents {
    private UiComponents() {}

    public static Button button(String text, Tone tone, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("action-button");
        button.getStyleClass().add(switch (tone) {
            case PRIMARY -> "action-primary";
            case SECONDARY -> "action-secondary";
            case DANGER -> "action-danger";
        });
        button.setMinHeight(36);
        button.setFocusTraversable(true);
        button.setOnAction(event -> action.run());
        return button;
    }

    public static VBox page(String title, String subtitle, Node... content) {
        Label heading = new Label(title);
        heading.getStyleClass().add("page-heading");
        Label description = new Label(subtitle);
        description.getStyleClass().add("page-description");
        VBox page = new VBox(18);
        page.getStyleClass().add("module-page");
        page.setPadding(new Insets(24, 28, 24, 28));
        page.getChildren().addAll(heading, description);
        page.getChildren().addAll(content);
        return page;
    }

    public static HBox sectionHeading(String title, String detail) {
        Label heading = new Label(title);
        heading.getStyleClass().add("section-heading");
        Label note = new Label(detail);
        note.getStyleClass().add("section-detail");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(10, heading, spacer, note);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    public static VBox panel(String heading, String detail, Node... content) {
        VBox panel = new VBox(12);
        panel.getStyleClass().add("surface-panel");
        panel.setPadding(new Insets(16));
        panel.getChildren().add(sectionHeading(heading, detail));
        panel.getChildren().addAll(content);
        return panel;
    }

    public static VBox metric(String labelText, String valueText, String trend, String toneClass) {
        Label label = new Label(labelText);
        label.getStyleClass().add("metric-caption");
        Label value = new Label(valueText);
        value.getStyleClass().addAll("metric-number", toneClass);
        Label subline = new Label(trend);
        subline.getStyleClass().add("metric-subline");
        VBox metric = new VBox(6, label, value, subline);
        metric.getStyleClass().add("metric-block");
        return metric;
    }

    public enum Tone { PRIMARY, SECONDARY, DANGER }
}
