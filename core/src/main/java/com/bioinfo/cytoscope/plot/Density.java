package com.bioinfo.cytoscope.plot;

import com.bioinfo.cytoscope.scale.Scale;

/**
 * Events counted on a square grid of axis positions: what a density plot draws. The events
 * outside the displayed range are piled on the edges, as cytometry software usually shows them.
 *
 * @param bins number of cells along each axis
 * @param counts number of events of each cell, column by column: {@code counts[x * bins + y]},
 *     y going upward
 * @param max largest count of a cell
 */
public record Density(int bins, int[] counts, int max) {

    /** Checks the grid and copies the counts. */
    public Density {
        if (bins < 1 || counts.length != bins * bins) {
            throw new IllegalArgumentException("The grid needs bins x bins counts");
        }
        counts = counts.clone();
    }

    @Override
    public int[] counts() {
        return counts.clone();
    }

    /**
     * Returns the count of one cell.
     *
     * @param x column, 0 on the left
     * @param y row, 0 at the bottom
     * @return the number of events
     */
    public int count(int x, int y) {
        return counts[x * bins + y];
    }

    /**
     * Counts events on a grid.
     *
     * @param x values on the horizontal axis
     * @param y values on the vertical axis
     * @param xScale scale of the horizontal axis
     * @param yScale scale of the vertical axis
     * @param bins number of cells along each axis
     * @return the grid
     */
    public static Density of(double[] x, double[] y, Scale xScale, Scale yScale, int bins) {
        if (x.length != y.length) {
            throw new IllegalArgumentException("One y value is needed per x value");
        }
        int[] counts = new int[bins * bins];
        int max = 0;
        for (int e = 0; e < x.length; e++) {
            int bx = bin(xScale.apply(x[e]), bins);
            int by = bin(yScale.apply(y[e]), bins);
            max = Math.max(max, ++counts[bx * bins + by]);
        }
        return new Density(bins, counts, max);
    }

    /**
     * Counts values along one axis, ignoring those outside the displayed range.
     *
     * @param values the values
     * @param scale scale of the axis
     * @param bins number of bars
     * @return the count of each bar
     */
    public static int[] histogram(double[] values, Scale scale, int bins) {
        int[] counts = new int[bins];
        for (double value : values) {
            double position = scale.apply(value);
            if (position >= 0 && position < 1) {
                counts[(int) (position * bins)]++;
            }
        }
        return counts;
    }

    private static int bin(double position, int bins) {
        if (!(position > 0)) {
            return 0;
        }
        return Math.min(bins - 1, (int) (position * bins));
    }
}
