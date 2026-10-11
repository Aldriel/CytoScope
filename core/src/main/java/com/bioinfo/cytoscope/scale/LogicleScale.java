package com.bioinfo.cytoscope.scale;

import java.util.Objects;

/**
 * Logicle scale (Parks, Roederer and Moore, Cytometry A 69:541, 2006).
 *
 * <p>The axis is the inverse of the biexponential function
 * {@code B(x) = a e^(bx) - c e^(-dx) + f}, which is linear around zero and logarithmic for large
 * values. Unlike the log scale it shows the negative values produced by compensation, so that a
 * negative population does not pile up on the axis.
 */
public final class LogicleScale implements Scale {

    /** Usual number of decades of a cytometer with 18-bit values. */
    public static final double DEFAULT_DECADES = 4.5;
    /** Usual width of the linear zone. */
    public static final double DEFAULT_WIDTH = 0.5;

    private final double top;
    private final double width;
    private final double decades;
    private final double extra;

    private final double a;
    private final double b;
    private final double c;
    private final double d;
    private final double f;
    private final double x1;

    /**
     * Creates the scale.
     *
     * @param top largest value, at position 1 (the range of the channel, often 262144)
     * @param width width of the linear zone, in decades (W, usually 0.5)
     * @param decades decades of the logarithmic part (M, usually 4.5)
     * @param extra additional negative decades (A, usually 0)
     */
    public LogicleScale(double top, double width, double decades, double extra) {
        if (!(top > 0) || !(decades > 0) || width < 0 || extra < 0 || 2 * width > decades) {
            throw new IllegalArgumentException(
                    "Logicle needs T > 0, M > 0, A >= 0 and 0 <= W <= M / 2");
        }
        this.top = top;
        this.width = width;
        this.decades = decades;
        this.extra = extra;

        double w = width / (decades + extra);
        double x2 = extra / (decades + extra);
        x1 = x2 + w;
        double x0 = x2 + 2 * w;
        b = (decades + extra) * Math.log(10);
        d = solveD(b, w);
        double ca = Math.exp(x0 * (b + d));
        double mfa = Math.exp(b * x1) - ca / Math.exp(d * x1);
        a = top / ((Math.exp(b) - mfa) - ca / Math.exp(d));
        c = ca * a;
        f = -mfa * a;
    }

    /**
     * Creates the usual logicle scale for a channel.
     *
     * @param top range of the channel
     * @return the scale with W = 0.5, M = 4.5 and A = 0
     */
    public static LogicleScale standard(double top) {
        return new LogicleScale(top, DEFAULT_WIDTH, DEFAULT_DECADES, 0);
    }

    /**
     * Chooses the width of the linear zone from the data, as recommended by Parks et al.: wide
     * enough for the 5th percentile of the negative values to stay on the axis.
     *
     * @param top range of the channel
     * @param lowPercentile 5th percentile of the values of the channel
     * @return the scale
     */
    public static LogicleScale forData(double top, double lowPercentile) {
        double w = DEFAULT_WIDTH;
        if (lowPercentile < 0) {
            w = (DEFAULT_DECADES - Math.log10(top / Math.abs(lowPercentile))) / 2;
        }
        w = Math.max(0, Math.min(DEFAULT_DECADES / 2, w));
        return new LogicleScale(top, w, DEFAULT_DECADES, 0);
    }

    /**
     * Returns the largest value of the axis (T).
     *
     * @return the top value
     */
    public double top() {
        return top;
    }

    /**
     * Returns the width of the linear zone in decades (W).
     *
     * @return the width
     */
    public double width() {
        return width;
    }

    /**
     * Returns the decades of the logarithmic part (M).
     *
     * @return the number of decades
     */
    public double decades() {
        return decades;
    }

    /**
     * Returns the additional negative decades (A).
     *
     * @return the number of extra decades
     */
    public double extra() {
        return extra;
    }

    @Override
    public ScaleType type() {
        return ScaleType.LOGICLE;
    }

    /** Solves {@code 2 (ln d - ln b) + w (b + d) = 0} for d, between 0 and b, by bisection. */
    private static double solveD(double b, double w) {
        if (w == 0) {
            return b;
        }
        double low = 0;
        double high = b;
        for (int i = 0; i < 200; i++) {
            double mid = (low + high) / 2;
            double value = 2 * (Math.log(mid) - Math.log(b)) + w * (b + mid);
            if (value > 0) {
                high = mid;
            } else {
                low = mid;
            }
        }
        return (low + high) / 2;
    }

    private double biexponential(double x) {
        return a * Math.exp(b * x) - c * Math.exp(-d * x) + f;
    }

    @Override
    public double invert(double position) {
        if (position < x1) {
            return -biexponential(2 * x1 - position);
        }
        return biexponential(position);
    }

    @Override
    public double apply(double value) {
        if (value == 0) {
            return x1;
        }
        if (value < 0) {
            return 2 * x1 - solve(-value);
        }
        return solve(value);
    }

    /** Finds x >= x1 with B(x) = value by Newton's method, kept inside a shrinking bracket. */
    private double solve(double value) {
        double low = x1;
        double high = 1;
        while (biexponential(high) < value) {
            high += 1;
        }
        double x = Math.max(low, Math.min(high, Math.log(value / a) / b));
        if (!(x > low && x < high)) {
            x = (low + high) / 2;
        }
        for (int i = 0; i < 100; i++) {
            double fx = biexponential(x) - value;
            if (fx > 0) {
                high = x;
            } else {
                low = x;
            }
            double slope = a * b * Math.exp(b * x) + c * d * Math.exp(-d * x);
            double next = x - fx / slope;
            if (!(next > low && next < high)) {
                next = (low + high) / 2;
            }
            if (Math.abs(next - x) < 1e-13) {
                return next;
            }
            x = next;
        }
        return x;
    }

    @Override
    public double[] ticks() {
        return Ticks.biexponential(invert(0), top);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof LogicleScale that && top == that.top && width == that.width
                && decades == that.decades && extra == that.extra;
    }

    @Override
    public int hashCode() {
        return Objects.hash(top, width, decades, extra);
    }

    @Override
    public String toString() {
        return "LogicleScale[T=" + top + ", W=" + width + ", M=" + decades + ", A=" + extra + "]";
    }
}
