package com.bioinfo.cytoscope.gating;

import java.util.Arrays;
import java.util.Objects;

/**
 * Polygon drawn point by point on a dot plot, the usual gate around a cell population.
 */
public final class PolygonGate implements Gate {

    private final String xChannel;
    private final String yChannel;
    private final double[] xs;
    private final double[] ys;

    /**
     * Creates the gate.
     *
     * @param xChannel channel of the horizontal axis
     * @param yChannel channel of the vertical axis
     * @param xs horizontal coordinates of the vertices
     * @param ys vertical coordinates of the vertices, in the same order
     */
    public PolygonGate(String xChannel, String yChannel, double[] xs, double[] ys) {
        this.xChannel = Objects.requireNonNull(xChannel, "xChannel");
        this.yChannel = Objects.requireNonNull(yChannel, "yChannel");
        if (xs.length != ys.length || xs.length < 3) {
            throw new IllegalArgumentException("A polygon needs at least three vertices");
        }
        for (int i = 0; i < xs.length; i++) {
            if (!Double.isFinite(xs[i]) || !Double.isFinite(ys[i])) {
                throw new IllegalArgumentException("The vertices of a gate must be numbers");
            }
        }
        this.xs = xs.clone();
        this.ys = ys.clone();
    }

    @Override
    public String xChannel() {
        return xChannel;
    }

    @Override
    public String yChannel() {
        return yChannel;
    }

    /**
     * Returns the horizontal coordinates of the vertices.
     *
     * @return a copy of the coordinates
     */
    public double[] xs() {
        return xs.clone();
    }

    /**
     * Returns the vertical coordinates of the vertices.
     *
     * @return a copy of the coordinates
     */
    public double[] ys() {
        return ys.clone();
    }

    /** Even-odd rule: a horizontal ray from the point crosses the edges an odd number of times. */
    @Override
    public boolean contains(double x, double y) {
        boolean inside = false;
        for (int i = 0, j = xs.length - 1; i < xs.length; j = i++) {
            if ((ys[i] > y) != (ys[j] > y)
                    && x < (xs[j] - xs[i]) * (y - ys[i]) / (ys[j] - ys[i]) + xs[i]) {
                inside = !inside;
            }
        }
        return inside;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PolygonGate that && xChannel.equals(that.xChannel)
                && yChannel.equals(that.yChannel) && Arrays.equals(xs, that.xs)
                && Arrays.equals(ys, that.ys);
    }

    @Override
    public int hashCode() {
        return Objects.hash(xChannel, yChannel, Arrays.hashCode(xs), Arrays.hashCode(ys));
    }

    @Override
    public String toString() {
        return "PolygonGate[" + xChannel + ", " + yChannel + ", " + xs.length + " vertices]";
    }
}
