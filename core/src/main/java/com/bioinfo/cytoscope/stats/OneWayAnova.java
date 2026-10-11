package com.bioinfo.cytoscope.stats;

import java.util.List;

/**
 * One-way analysis of variance: tests whether the means of several groups differ more than their
 * scatter explains.
 *
 * @param groupCount number of groups with at least one value
 * @param totalCount number of values
 * @param betweenSquares sum of squares between the groups
 * @param withinSquares sum of squares within the groups
 * @param f the F statistic
 * @param pValue probability of an F at least this large if all the means are equal
 */
public record OneWayAnova(int groupCount, int totalCount, double betweenSquares,
                          double withinSquares, double f, double pValue) {

    /**
     * Returns the degrees of freedom between the groups.
     *
     * @return number of groups minus 1
     */
    public int betweenDegrees() {
        return groupCount - 1;
    }

    /**
     * Returns the degrees of freedom within the groups.
     *
     * @return number of values minus number of groups
     */
    public int withinDegrees() {
        return totalCount - groupCount;
    }

    /**
     * Returns the pooled variance of the groups, the error mean square.
     *
     * @return within sum of squares divided by its degrees of freedom
     */
    public double meanSquareError() {
        return withinSquares / withinDegrees();
    }

    /**
     * Runs the test.
     *
     * @param groups values of each group; NaN values are ignored
     * @return the result
     * @throws IllegalArgumentException with fewer than two groups or no replicate at all
     */
    public static OneWayAnova of(List<double[]> groups) {
        int k = 0;
        int n = 0;
        double grandSum = 0;
        for (double[] group : groups) {
            int size = count(group);
            if (size > 0) {
                k++;
                n += size;
                grandSum += Descriptive.mean(group) * size;
            }
        }
        if (k < 2 || n - k < 1) {
            throw new IllegalArgumentException(
                    "The analysis of variance needs two groups and at least one replicate");
        }
        double grandMean = grandSum / n;
        double between = 0;
        double within = 0;
        for (double[] group : groups) {
            int size = count(group);
            if (size == 0) {
                continue;
            }
            double mean = Descriptive.mean(group);
            between += size * (mean - grandMean) * (mean - grandMean);
            for (double value : group) {
                if (!Double.isNaN(value)) {
                    within += (value - mean) * (value - mean);
                }
            }
        }
        double f = (between / (k - 1)) / (within / (n - k));
        double p;
        if (within == 0) {
            f = between == 0 ? 0 : Double.POSITIVE_INFINITY;
            p = between == 0 ? 1 : 0;
        } else {
            p = Distributions.fisherUpperTail(f, k - 1, n - k);
        }
        return new OneWayAnova(k, n, between, within, f, p);
    }

    static int count(double[] values) {
        int n = 0;
        for (double v : values) {
            if (!Double.isNaN(v)) {
                n++;
            }
        }
        return n;
    }
}
