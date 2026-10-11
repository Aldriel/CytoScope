package com.bioinfo.cytoscope.scale;

/**
 * Inverse hyperbolic sine scale: {@code asinh(value / cofactor)}, linear for values small
 * compared to the cofactor and logarithmic beyond.
 *
 * @param min value at position 0, often negative
 * @param max value at position 1
 * @param cofactor width of the linear zone; 150 is usual for conventional cytometers
 */
public record ArcsinhScale(double min, double max, double cofactor) implements Scale {

    /** Creates the scale and checks its parameters. */
    public ArcsinhScale {
        if (!(max > min) || !(cofactor > 0)) {
            throw new IllegalArgumentException("An arcsinh scale needs min < max and cofactor > 0");
        }
    }

    @Override
    public ScaleType type() {
        return ScaleType.ARCSINH;
    }

    @Override
    public double apply(double value) {
        double low = asinh(min / cofactor);
        return (asinh(value / cofactor) - low) / (asinh(max / cofactor) - low);
    }

    @Override
    public double invert(double position) {
        double low = asinh(min / cofactor);
        double transformed = low + position * (asinh(max / cofactor) - low);
        return cofactor * Math.sinh(transformed);
    }

    @Override
    public double[] ticks() {
        return Ticks.biexponential(min, max);
    }

    static double asinh(double x) {
        return Math.log(x + Math.sqrt(x * x + 1));
    }
}
