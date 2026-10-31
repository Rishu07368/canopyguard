package com.canopyguard.ui;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;

final class ModuleGlyph extends Canvas {
    ModuleGlyph(String module) {
        super(16, 16);
        setMouseTransparent(true);
        GraphicsContext g = getGraphicsContext2D();
        g.setStroke(Color.web("#aebba6"));
        g.setFill(Color.web("#aebba6"));
        g.setLineWidth(1.35);
        g.setLineCap(StrokeLineCap.ROUND);
        switch (module) {
            case "Overview" -> {
                g.strokeRect(2, 2, 5, 5); g.strokeRect(9, 2, 5, 5);
                g.strokeRect(2, 9, 5, 5); g.strokeRect(9, 9, 5, 5);
            }
            case "Geographic intelligence" -> {
                g.strokeOval(3, 2, 10, 10); g.strokeLine(5, 10, 8, 15);
                g.strokeLine(11, 10, 8, 15); g.fillOval(6, 5, 4, 4);
            }
            case "Fire intelligence" -> {
                g.beginPath(); g.moveTo(8, 1); g.lineTo(12, 6); g.lineTo(11, 9);
                g.lineTo(14, 8); g.lineTo(14, 12); g.lineTo(11, 15); g.lineTo(5, 15);
                g.lineTo(2, 11); g.lineTo(4, 7); g.lineTo(6, 9); g.closePath(); g.stroke();
                g.strokeLine(8, 8, 7, 12); g.strokeLine(8, 8, 10, 12);
            }
            case "Community protection" -> {
                g.strokeOval(5, 2, 6, 6); g.strokeArc(2, 8, 12, 8, 0, 180, ArcType.OPEN);
                g.strokeArc(0, 7, 7, 8, 0, 180, ArcType.OPEN); g.strokeArc(9, 7, 7, 8, 0, 180, ArcType.OPEN);
            }
            case "Evacuation planning" -> {
                g.strokeLine(3, 13, 7, 8); g.strokeLine(7, 8, 12, 4); g.strokeLine(7, 8, 13, 12);
                g.fillOval(1, 11, 4, 4); g.fillOval(10, 2, 4, 4); g.fillOval(11, 10, 4, 4);
            }
            case "Wildlife & habitat" -> {
                g.strokeOval(2, 4, 6, 9); g.strokeOval(8, 3, 6, 9);
                g.strokeLine(4, 11, 12, 5); g.strokeLine(7, 13, 13, 9);
            }
            case "Emergency resources" -> {
                g.strokeRect(2, 4, 12, 10); g.strokeLine(2, 7, 14, 7);
                g.strokeLine(6, 2, 10, 2); g.strokeLine(6, 2, 6, 4); g.strokeLine(10, 2, 10, 4);
            }
            case "Incident history" -> {
                g.strokeOval(2, 2, 12, 12); g.strokeLine(8, 4, 8, 8); g.strokeLine(8, 8, 11, 10);
            }
            case "Reports" -> {
                g.strokeRect(3, 1, 10, 14); g.strokeLine(5, 5, 11, 5);
                g.strokeLine(5, 8, 11, 8); g.strokeLine(5, 11, 10, 11);
            }
            default -> {
                g.strokeOval(4, 4, 8, 8); g.strokeOval(6, 6, 4, 4);
                for (int angle = 0; angle < 360; angle += 45) {
                    double radians = Math.toRadians(angle);
                    g.strokeLine(8 + Math.cos(radians) * 5, 8 + Math.sin(radians) * 5,
                            8 + Math.cos(radians) * 7, 8 + Math.sin(radians) * 7);
                }
            }
        }
    }
}
