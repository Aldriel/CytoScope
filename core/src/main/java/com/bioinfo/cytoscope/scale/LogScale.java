package com.bioinfo.cytoscope.scale;

/**
 * Decimal logarithmic scale. Values at or below zero cannot be shown: they are placed at the
 * minimum of the axis.
 *
 * @param min smallest value shown, at position 0; positive
 * @param max largest value shown, at position 1
 */
public record LogScale(double min, double max) implements Scale {

    /** Creates the scale and checks its bounds. */
    public LogScale {
        if (!(min > 0) || !(max > min)) {
            throw new IllegalArgumentException("A log scale needs 0 < minimum < maximum");
        }
    }

    @Override
    public ScaleType type() {
        return ScaleType.LOG;
    }

    @Override
    public double apply(double value) {
        double clamped = Math.max(value, min);
        return Math.log10(clamped / min) / Math.log10(max / min);
    }

    @Override
    public double invert(double position) {
        return min * Math.pow(max / min, position);
    }

    @Override
    public double[] ticks() {
        int first = (int) Math.ceil(Math.log10(min) - 1e-9);
        int last = (int) Math.floor(Math.log10(max) + 1e-9);
        double[] ticks = new double[Math.max(0, last - first + 1)];
        for (int i = 0; i < ticks.length; i++) {
            ticks[i] = Math.pow(10, first + i);
        }
        return ticks;
    }
}
