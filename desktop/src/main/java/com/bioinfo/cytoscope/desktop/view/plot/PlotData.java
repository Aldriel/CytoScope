package com.bioinfo.cytoscope.desktop.view.plot;

import com.bioinfo.cytoscope.fcs.Channel;
import com.bioinfo.cytoscope.fcs.FcsSample;
import com.bioinfo.cytoscope.gating.GatingResult;
import com.bioinfo.cytoscope.plot.Density;
import com.bioinfo.cytoscope.scale.Scale;
import com.bioinfo.cytoscope.scale.ScaleType;
import com.bioinfo.cytoscope.scale.Scales;

/**
 * What a plot draws: the events of one population counted on a grid (or along one axis for a
 * histogram), with the scales of the axes.
 *
 * @param x channel of the horizontal axis
 * @param xScale scale of the horizontal axis
 * @param y channel of the vertical axis; null for a histogram
 * @param yScale scale of the vertical axis; null for a histogram
 * @param density events counted on a grid; null for a histogram
 * @param histogram events counted along the horizontal axis; null for a density plot
 * @param events number of events drawn
 */
public record PlotData(
        Channel x,
        Scale xScale,
        Channel y,
        Scale yScale,
        Density density,
        int[] histogram,
        int events) {

    /** Number of cells of the grid along each axis. */
    public static final int BINS = 128;

    /** Number of bars of a histogram. */
    public static final int HISTOGRAM_BINS = 256;

    /** Copies the histogram. */
    public PlotData {
        histogram = histogram == null ? null : histogram.clone();
    }

    @Override
    public int[] histogram() {
        return histogram == null ? null : histogram.clone();
    }

    /**
     * Counts the events of a population of a gated tube.
     *
     * @param gating the gated tube
     * @param populationId the population drawn
     * @param x channel of the horizontal axis
     * @param y channel of the vertical axis; null for a histogram
     * @param xType scale of the horizontal axis; the default of the channel if null
     * @param yType scale of the vertical axis; the default of the channel if null
     * @param dnaChannel channel of the DNA content, linear by default; may be null
     * @return the plot
     * @throws IllegalArgumentException if a channel or the population is unknown
     */
    public static PlotData of(
            GatingResult gating,
            String populationId,
            String x,
            String y,
            ScaleType xType,
            ScaleType yType,
            String dnaChannel) {
        if (gating.tree().find(populationId).isEmpty()) {
            throw new IllegalArgumentException("Unknown population " + populationId);
        }
        FcsSample sample = gating.sample();
        int xi = sample.requireChannel(x);
        Channel xChannel = sample.channels().get(xi);
        double[] xValues = gating.values(populationId, xi);
        Scale xScale = scale(xChannel, xType, xValues, dnaChannel);
        if (y == null) {
            return new PlotData(
                    xChannel,
                    xScale,
                    null,
                    null,
                    null,
                    Density.histogram(xValues, xScale, HISTOGRAM_BINS),
                    xValues.length);
        }
        int yi = sample.requireChannel(y);
        Channel yChannel = sample.channels().get(yi);
        double[] yValues = gating.values(populationId, yi);
        Scale yScale = scale(yChannel, yType, yValues, dnaChannel);
        return new PlotData(
                xChannel,
                xScale,
                yChannel,
                yScale,
                Density.of(xValues, yValues, xScale, yScale, BINS),
                null,
                xValues.length);
    }

    private static Scale scale(Channel channel, ScaleType type, double[] values, String dna) {
        return Scales.create(
                type == null ? Scales.defaultType(channel, dna) : type, channel, values);
    }
}
