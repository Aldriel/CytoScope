package com.bioinfo.cytoscope.gating;

import java.util.Objects;

/**
 * Range of values of a single channel, drawn on a histogram.
 *
 * @param xChannel the channel
 * @param min lowest value selected
 * @param max highest value selected
 */
public record IntervalGate(String xChannel, double min, double max) implements Gate {

    /** Creates the gate and orders its bounds. */
    public IntervalGate {
        Objects.requireNonNull(xChannel, "xChannel");
        if (Double.isNaN(min) || Double.isNaN(max)) {
            throw new IllegalArgumentException("The bounds of a gate must be numbers");
        }
        if (min > max) {
            double swap = min;
            min = max;
            max = swap;
        }
    }

    @Override
    public String yChannel() {
        return null;
    }

    @Override
    public boolean contains(double x, double y) {
        return x >= min && x <= max;
    }
}
