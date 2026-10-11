package com.bioinfo.cytoscope.compensation;

import com.bioinfo.cytoscope.fcs.FcsSample;
import com.bioinfo.cytoscope.stats.Descriptive;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Computes a spillover matrix from single-stain controls, as cytometrists do when the matrix of
 * the instrument does not fit the experiment.
 *
 * <p>Each control is stained with one fluorochrome only. Its positive events are those brighter,
 * in its own channel, than 99.5 % of the negative reference: the unstained control if there is
 * one, otherwise the dimmest tenth of the same control. For each positive event, the rise above
 * the negative median in another channel divided by the rise in the own channel estimates the
 * spillover; the median of these ratios is robust to the dim events and to the debris.
 */
public final class SpilloverEstimator {

    /** Percentile of the negative reference above which an event counts as positive. */
    static final double POSITIVE_PERCENTILE = 99.5;
    /** Share of the dimmest events of a control used as negative reference without unstained. */
    static final double DIM_PERCENT = 10;
    private static final int MIN_POSITIVE_EVENTS = 20;
    /**
     * Share of positive events below which a tube is not a stained control: by definition, 0.5 %
     * of an unstained tube lies above the 99.5th percentile.
     */
    private static final double MIN_POSITIVE_SHARE = 0.02;

    private SpilloverEstimator() {}

    /**
     * Computes the matrix.
     *
     * @param controls single-stain control of each channel to compensate, by channel name; the
     *     values must not be compensated
     * @param unstained unstained control, or null if there is none
     * @return the spillover matrix, with the channels in the order of the map
     * @throws IllegalArgumentException if a control lacks a channel or has too few events
     *     brighter than the negative reference in its own channel
     */
    public static SpilloverMatrix estimate(Map<String, FcsSample> controls, FcsSample unstained) {
        List<String> channels = new ArrayList<>(controls.keySet());
        int n = channels.size();
        double[][] values = new double[n][n];
        for (int i = 0; i < n; i++) {
            FcsSample control = controls.get(channels.get(i));
            double[][] columns = new double[n][];
            for (int j = 0; j < n; j++) {
                columns[j] = toDouble(control.column(control.requireChannel(channels.get(j))));
            }
            double[] primary = columns[i];
            double[] negativeMedians = new double[n];
            double threshold;
            if (unstained == null) {
                double dim = Descriptive.percentile(primary, DIM_PERCENT);
                for (int j = 0; j < n; j++) {
                    negativeMedians[j] = Descriptive.median(where(columns[j], primary, dim));
                }
                threshold = Descriptive.percentile(where(primary, primary, dim),
                        POSITIVE_PERCENTILE);
            } else {
                for (int j = 0; j < n; j++) {
                    negativeMedians[j] = Descriptive.median(toDouble(
                            unstained.column(unstained.requireChannel(channels.get(j)))));
                }
                threshold = Descriptive.percentile(toDouble(
                        unstained.column(unstained.requireChannel(channels.get(i)))),
                        POSITIVE_PERCENTILE);
            }
            List<Integer> positive = new ArrayList<>();
            for (int e = 0; e < primary.length; e++) {
                if (primary[e] > threshold && primary[e] > negativeMedians[i]) {
                    positive.add(e);
                }
            }
            if (positive.size() < Math.max(MIN_POSITIVE_EVENTS,
                    MIN_POSITIVE_SHARE * primary.length)) {
                throw new IllegalArgumentException("The control of " + channels.get(i)
                        + " has too few events brighter than the negative reference");
            }
            for (int j = 0; j < n; j++) {
                if (i == j) {
                    values[i][j] = 1;
                    continue;
                }
                double[] ratios = new double[positive.size()];
                for (int k = 0; k < ratios.length; k++) {
                    int e = positive.get(k);
                    ratios[k] = (columns[j][e] - negativeMedians[j])
                            / (primary[e] - negativeMedians[i]);
                }
                values[i][j] = Math.max(0, Descriptive.median(ratios));
            }
        }
        return new SpilloverMatrix(channels, values);
    }

    /** Values of a column for the events whose primary value is at or below a bound. */
    private static double[] where(double[] column, double[] primary, double bound) {
        double[] selected = new double[column.length];
        int count = 0;
        for (int e = 0; e < column.length; e++) {
            if (primary[e] <= bound) {
                selected[count++] = column[e];
            }
        }
        return Arrays.copyOf(selected, count);
    }

    private static double[] toDouble(float[] values) {
        double[] result = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = values[i];
        }
        return result;
    }
}
