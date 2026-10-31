package com.canopyguard.domain;

public record FireCell(int x, int y, double fuel, double moisture, double slope,
                       FireState state, double intensity, double smoke) {
    public enum FireState { UNBURNED, HEATING, BURNING, BURNED }
}
