package com.bioinfo.cytoscope.scale;

/**
 * Linear scale between a minimum and a maximum value.
 *
 * @param min value at position 0
 * @param max value at position 1
 */
public record LinearScale(double min, double max) implements Scale {

    /** Creates the scale and checks its bounds. */
    public LinearScale {
        if (!(max > min)) {
            throw new IllegalArgumentException("The maximum must be greater than the minimum");
        }
    }

    @Override
    public ScaleType type() {
        return ScaleType.LINEAR;
    }

    @Override
    public double apply(double value) {
        return (value - min) / (max - min);
    }

    @Override
    public double invert(double position) {
        return min + position * (max - min);
    }

    @Override
    public double[] ticks() {
        double span = max - min;
        double step = Math.pow(10, Math.floor(Math.log10(span)));
        if (span / step < 3) {
            step /= 2;
        }
        double first = Math.ceil(min / step) * step;
        int count = (int) Math.floor((max - first) / step + 1e-9) + 1;
        double[] ticks = new double[count];
        for (int i = 0; i < count; i++) {
            ticks[i] = first + i * step;
        }
        return ticks;
    }
}
