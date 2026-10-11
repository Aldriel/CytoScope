package com.bioinfo.cytoscope.scale;

import java.util.ArrayList;
import java.util.List;

/** Tick values shared by the scales that show negative and positive decades. */
final class Ticks {

    private Ticks() {}

    /**
     * Returns 0 and the powers of ten, negative and positive, between two bounds.
     *
     * @param min lowest value of the axis
     * @param max highest value of the axis
     * @return the ticks in increasing order
     */
    static double[] biexponential(double min, double max) {
        List<Double> ticks = new ArrayList<>();
        for (int exponent = 9; exponent >= 1; exponent--) {
            double value = -Math.pow(10, exponent);
            if (value >= min) {
                ticks.add(value);
            }
        }
        if (min <= 0 && max >= 0) {
            ticks.add(0.0);
        }
        for (int exponent = 1; exponent <= 9; exponent++) {
            double value = Math.pow(10, exponent);
            if (value <= max && value >= min) {
                ticks.add(value);
            }
        }
        return ticks.stream().mapToDouble(Double::doubleValue).toArray();
    }
}
