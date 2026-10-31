package com.canopyguard.ui;

import com.canopyguard.domain.FireCell;
import com.canopyguard.domain.FireCell.FireState;
import com.canopyguard.engine.WildfireEngine;
import com.canopyguard.geo.EmeraldReachScenario;
import com.canopyguard.geo.GeographicFeature;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseButton;
import javafx.scene.paint.Color;
import org.locationtech.jts.geom.*;

import java.util.*;
import java.util.function.Consumer;

public final class MapCanvas extends Canvas {
    private final EmeraldReachScenario scenario;
    private final EnumMap<Layer, Boolean> layers = new EnumMap<>(Layer.class);
    private WildfireEngine.Snapshot fire;
    private GeographicFeature selected;
    private List<Coordinate> route = List.of();
    private Consumer<GeographicFeature> selectionHandler = ignored -> {};
    private double zoom = 1, panX, panY, dragX, dragY, pressX, pressY;
    private boolean dragging, dragged;
    public enum Layer { FOREST, WATER, INFRASTRUCTURE, COMMUNITIES, HABITATS, FIRE, SMOKE }

    public MapCanvas(EmeraldReachScenario scenario, double width, double height) {
        super(width, height);
        this.scenario = scenario;
        for (Layer layer : Layer.values()) layers.put(layer, true);
        setOnMousePressed(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                dragging = true;
                dragged = false;
                pressX = event.getX();
                pressY = event.getY();
                dragX = event.getX() - panX;
                dragY = event.getY() - panY;
            }
        });
        setOnMouseDragged(event -> {
            if (dragging) {
                if (Math.hypot(event.getX() - pressX, event.getY() - pressY) > 4) dragged = true;
                panX = event.getX() - dragX;
                panY = event.getY() - dragY;
                draw();
            }
        });
        setOnMouseReleased(event -> dragging = false);
        setOnScroll(event -> {
            double previousZoom = zoom;
            double factor = event.getDeltaY() > 0 ? 1.12 : 1 / 1.12;
            zoom = Math.max(0.7, Math.min(7, zoom * factor));
            double appliedFactor = zoom / previousZoom;
            panX = event.getX() - getWidth() / 2
                    - (event.getX() - getWidth() / 2 - panX) * appliedFactor;
            panY = event.getY() - getHeight() / 2
                    - (event.getY() - getHeight() / 2 - panY) * appliedFactor;
            draw();
            event.consume();
        });
        setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && !dragged) {
                selected = pick(event.getX(), event.getY());
                selectionHandler.accept(selected);
                draw();
            }
            dragged = false;
        });
        draw();
    }

    public void setSelectionHandler(Consumer<GeographicFeature> handler) { selectionHandler = handler; }
    public void setFireSnapshot(WildfireEngine.Snapshot fire) { this.fire = fire; draw(); }
    public void setLayerVisible(Layer layer, boolean visible) { layers.put(layer, visible); draw(); }
    public void resizeMap(double width, double height) {
        if (!Double.isFinite(width) || !Double.isFinite(height) || width <= 0 || height <= 0) return;
        if (Double.compare(getWidth(), width) == 0 && Double.compare(getHeight(), height) == 0) return;
        setWidth(width);
        setHeight(height);
        draw();
    }
    public void setSelectedFeature(GeographicFeature feature) {
        selected = feature;
        selectionHandler.accept(feature);
        draw();
    }
    public void setRoute(List<Coordinate> coordinates) { route = List.copyOf(coordinates); draw(); }
    public void clearRoute() { route = List.of(); draw(); }
    public void zoomIn() { zoom = Math.min(7, zoom * 1.2); draw(); }
    public void zoomOut() { zoom = Math.max(0.7, zoom / 1.2); draw(); }
    public void resetView() { zoom = 1; panX = 0; panY = 0; draw(); }

    private void draw() {
        GraphicsContext gc = getGraphicsContext2D();
        double width = getWidth(), height = getHeight();
        gc.setFill(Color.web("#17251f")); gc.fillRect(0, 0, width, height);
        gc.setStroke(Color.web("#33433a")); gc.setLineWidth(1);
        for (int i = 1; i < 9; i++) {
            gc.strokeLine(width * i / 9, 0, width * i / 9, height);
            gc.strokeLine(0, height * i / 9, width, height * i / 9);
        }
        for (GeographicFeature feature : scenario.features()) {
            Layer layer = layer(feature.kind());
            if (layer == null || !layers.get(layer)) continue;
            drawGeometry(gc, feature.geometry(), feature);
        }
        if (route.size() > 1) {
            Coordinates coordinates = coordinates(route.toArray(Coordinate[]::new));
            gc.setStroke(Color.web("#f0d47a"));
            gc.setLineWidth(4);
            gc.setLineDashes(9, 5);
            gc.strokePolyline(coordinates.x, coordinates.y, coordinates.x.length);
            gc.setLineDashes();
        }
        if (fire != null && layers.get(Layer.FIRE)) drawFire(gc, fire, false);
        if (fire != null && layers.get(Layer.SMOKE)) drawFire(gc, fire, true);
        gc.setFill(Color.web("#e2e8df"));
        gc.fillText("EMERALD REACH  ·  FICTIONAL TRAINING REGION  ·  NOT A NAVIGATIONAL MAP", 18, height - 18);
        gc.setFill(Color.web("#9ba99f")); gc.fillText("WGS 84  |  Zoom " + String.format(Locale.ROOT, "%.1fx", zoom), 18, 22);
    }

    private void drawGeometry(GraphicsContext gc, Geometry geometry, GeographicFeature feature) {
        Color color = color(feature.kind());
        gc.setStroke(color); gc.setFill(color);
        gc.setLineWidth(feature == selected ? 3.4 : (feature.kind().equals("water") ? 3 : 1.5));
        if (geometry instanceof Polygon polygon) {
            Coordinates coordinates = coordinates(polygon.getExteriorRing().getCoordinates());
            gc.setFill(withAlpha(color, feature.kind().equals("forest") ? 0.20 : 0.12));
            gc.fillPolygon(coordinates.x, coordinates.y, coordinates.x.length);
            gc.setStroke(feature == selected ? Color.WHITE : color);
            gc.strokePolygon(coordinates.x, coordinates.y, coordinates.x.length);
        } else if (geometry instanceof LineString line) {
            Coordinates coordinates = coordinates(line.getCoordinates());
            gc.strokePolyline(coordinates.x, coordinates.y, coordinates.x.length);
            if (feature.kind().equals("corridor")) {
                gc.setLineDashes(4, 5); gc.strokePolyline(coordinates.x, coordinates.y, coordinates.x.length); gc.setLineDashes();
            }
        } else if (geometry instanceof Point point) {
            Point2D screen = project(point.getX(), point.getY());
            double radius = feature.kind().equals("ignition") ? 7 : 6;
            gc.fillOval(screen.x - radius, screen.y - radius, radius * 2, radius * 2);
            gc.setStroke(Color.web("#f5f2e9")); gc.setLineWidth(1.4);
            gc.strokeOval(screen.x - radius - 2, screen.y - radius - 2, radius * 2 + 4, radius * 2 + 4);
            gc.setFill(Color.web("#f1f0e9")); gc.fillText(feature.name(), screen.x + 9, screen.y - 7);
        } else {
            for (int i = 0; i < geometry.getNumGeometries(); i++) drawGeometry(gc, geometry.getGeometryN(i), feature);
        }
    }

    private void drawFire(GraphicsContext gc, WildfireEngine.Snapshot snapshot, boolean showSmoke) {
        double cellWidth = (scenario.maxLongitude() - scenario.minLongitude()) / snapshot.width();
        double cellHeight = (scenario.maxLatitude() - scenario.minLatitude()) / snapshot.height();
        for (FireCell cell : snapshot.cells()) {
            double amount = showSmoke ? cell.smoke() : (cell.state() == FireState.BURNING ? cell.intensity() : cell.state() == FireState.BURNED ? 0.2 : 0);
            if (amount < (showSmoke ? 0.07 : 0.08)) continue;
            double lon = scenario.minLongitude() + (cell.x() + 0.5) * cellWidth;
            double lat = scenario.maxLatitude() - (cell.y() + 0.5) * cellHeight;
            Point2D p = project(lon, lat);
            double w = Math.abs(project(lon + cellWidth, lat).x - p.x) + 1;
            double h = Math.abs(project(lon, lat - cellHeight).y - p.y) + 1;
            gc.setFill(showSmoke ? Color.color(0.72, 0.76, 0.72, Math.min(0.54, amount * 0.55))
                    : cell.state() == FireState.BURNED ? Color.color(0.12, 0.09, 0.07, 0.55)
                    : Color.color(1, 0.27, 0.06, Math.min(0.9, amount * 0.86)));
            gc.fillRect(p.x - w / 2, p.y - h / 2, w, h);
        }
    }

    private GeographicFeature pick(double screenX, double screenY) {
        double lon = scenario.minLongitude() + (screenX - offsetX()) / scaleX();
        double lat = scenario.maxLatitude() - (screenY - offsetY()) / scaleY();
        double tolerance = (scenario.maxLongitude() - scenario.minLongitude()) / (getWidth() * zoom) * 12;
        return scenario.features().stream().filter(f -> f.geometry().distance(new GeometryFactory().createPoint(new Coordinate(lon, lat))) < tolerance)
                .min(Comparator.comparingDouble(f -> f.geometry().distance(new GeometryFactory().createPoint(new Coordinate(lon, lat)))))
                .orElse(null);
    }

    private Layer layer(String kind) {
        return switch (kind) {
            case "forest" -> Layer.FOREST;
            case "water" -> Layer.WATER;
            case "road" -> Layer.INFRASTRUCTURE;
            case "community", "shelter" -> Layer.COMMUNITIES;
            case "habitat", "corridor" -> Layer.HABITATS;
            case "ignition" -> Layer.FIRE;
            default -> null;
        };
    }
    private Color color(String kind) {
        return switch (kind) {
            case "forest" -> Color.web("#718c60");
            case "water" -> Color.web("#70b8bd");
            case "road" -> Color.web("#d8c694");
            case "community" -> Color.web("#e6d9bb");
            case "shelter" -> Color.web("#80c6a2");
            case "habitat" -> Color.web("#bbd28b");
            case "corridor" -> Color.web("#bfdc88");
            case "ignition" -> Color.web("#ff7547");
            default -> Color.WHITE;
        };
    }
    private Color withAlpha(Color color, double alpha) { return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha); }

    private Coordinates coordinates(Coordinate[] coordinates) {
        double[] x = new double[coordinates.length], y = new double[coordinates.length];
        for (int i = 0; i < coordinates.length; i++) {
            Point2D p = project(coordinates[i].x, coordinates[i].y); x[i] = p.x; y[i] = p.y;
        }
        return new Coordinates(x, y);
    }
    private Point2D project(double lon, double lat) {
        return new Point2D(offsetX() + (lon - scenario.minLongitude()) * scaleX(),
                offsetY() + (scenario.maxLatitude() - lat) * scaleY());
    }
    private double scaleX() { return Math.min(getWidth() / (scenario.maxLongitude() - scenario.minLongitude()), getHeight() / (scenario.maxLatitude() - scenario.minLatitude())) * zoom; }
    private double scaleY() { return scaleX(); }
    private double offsetX() { return (getWidth() - (scenario.maxLongitude() - scenario.minLongitude()) * scaleX()) / 2 + panX; }
    private double offsetY() { return (getHeight() - (scenario.maxLatitude() - scenario.minLatitude()) * scaleY()) / 2 + panY; }
    private record Coordinates(double[] x, double[] y) {}
    private record Point2D(double x, double y) {}
}
