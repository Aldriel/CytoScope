package com.bioinfo.cytoscope.stats;

import java.util.Arrays;

/**
 * Descriptive statistics of a series of values: mean, standard deviation, median, percentiles
 * and coefficient of variation. Missing values (NaN) are ignored.
 */
public final class Descriptive {

    private Descriptive() {}

    /**
     * Returns the arithmetic mean.
     *
     * @param values the values
     * @return the mean, or NaN if there is no value
     */
    public static double mean(double[] values) {
        double sum = 0;
        int n = 0;
        for (double v : values) {
            if (!Double.isNaN(v)) {
                sum += v;
                n++;
            }
        }
        return n == 0 ? Double.NaN : sum / n;
    }

    /**
     * Returns the sample standard deviation (divided by n - 1).
     *
     * @param values the values
     * @return the standard deviation, or NaN with fewer than two values
     */
    public static double standardDeviation(double[] values) {
        double mean = mean(values);
        double sum = 0;
        int n = 0;
        for (double v : values) {
            if (!Double.isNaN(v)) {
                sum += (v - mean) * (v - mean);
                n++;
            }
        }
        return n < 2 ? Double.NaN : Math.sqrt(sum / (n - 1));
    }

    /**
     * Returns the median.
     *
     * @param values the values
     * @return the median, or NaN if there is no value
     */
    public static double median(double[] values) {
        return percentile(values, 50);
    }

    /**
     * Returns a percentile, by linear interpolation between the two nearest ranks.
     *
     * @param values the values
     * @param percent between 0 and 100
     * @return the percentile, or NaN if there is no value
     */
    public static double percentile(double[] values, double percent) {
        double[] sorted = Arrays.stream(values).filter(v -> !Double.isNaN(v)).sorted().toArray();
        return percentileOfSorted(sorted, percent);
    }

    /**
     * Returns a percentile of values already sorted in increasing order.
     *
     * @param sorted the sorted values, without NaN
     * @param percent between 0 and 100
     * @return the percentile, or NaN if there is no value
     */
    public static double percentileOfSorted(double[] sorted, double percent) {
        if (sorted.length == 0) {
            return Double.NaN;
        }
        double rank = Math.max(0, Math.min(100, percent)) / 100 * (sorted.length - 1);
        int low = (int) Math.floor(rank);
        int high = Math.min(sorted.length - 1, low + 1);
        return sorted[low] + (rank - low) * (sorted[high] - sorted[low]);
    }

    /**
     * Returns the coefficient of variation, in percent of the mean.
     *
     * @param values the values
     * @return the CV, or NaN if the mean is 0 or there are fewer than two values
     */
    public static double coefficientOfVariation(double[] values) {
        double mean = mean(values);
        return mean == 0 ? Double.NaN : 100 * standardDeviation(values) / Math.abs(mean);
    }

    /**
     * Returns the robust coefficient of variation used in cytometry: the half-width of the
     * central 68.26 % of the values, divided by the median.
     *
     * @param values the values
     * @return the robust CV in percent, or NaN if the median is 0 or there is no value
     */
    public static double robustCoefficientOfVariation(double[] values) {
        double[] sorted = Arrays.stream(values).filter(v -> !Double.isNaN(v)).sorted().toArray();
        double median = percentileOfSorted(sorted, 50);
        if (sorted.length == 0 || median == 0) {
            return Double.NaN;
        }
        double spread = (percentileOfSorted(sorted, 84.13) - percentileOfSorted(sorted, 15.87)) / 2;
        return 100 * spread / Math.abs(median);
    }
}
