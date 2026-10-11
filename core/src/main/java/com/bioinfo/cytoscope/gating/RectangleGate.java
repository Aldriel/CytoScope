package com.bioinfo.cytoscope.gating;

import java.util.Objects;

/**
 * Rectangle on a dot plot of two channels.
 *
 * @param xChannel channel of the horizontal axis
 * @param yChannel channel of the vertical axis
 * @param xMin left edge
 * @param xMax right edge
 * @param yMin bottom edge
 * @param yMax top edge
 */
public record RectangleGate(String xChannel, String yChannel,
                            double xMin, double xMax, double yMin, double yMax) implements Gate {

    /** Creates the gate and orders its edges. */
    public RectangleGate {
        Objects.requireNonNull(xChannel, "xChannel");
        Objects.requireNonNull(yChannel, "yChannel");
        if (Double.isNaN(xMin) || Double.isNaN(xMax) || Double.isNaN(yMin) || Double.isNaN(yMax)) {
            throw new IllegalArgumentException("The edges of a gate must be numbers");
        }
        if (xMin > xMax) {
            double swap = xMin;
            xMin = xMax;
            xMax = swap;
        }
        if (yMin > yMax) {
            double swap = yMin;
            yMin = yMax;
            yMax = swap;
        }
    }

    @Override
    public boolean contains(double x, double y) {
        return x >= xMin && x <= xMax && y >= yMin && y <= yMax;
    }
}
