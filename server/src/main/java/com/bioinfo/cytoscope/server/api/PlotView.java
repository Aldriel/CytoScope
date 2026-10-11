package com.bioinfo.cytoscope.server.api;

import com.bioinfo.cytoscope.experiment.Experiment;
import com.bioinfo.cytoscope.experiment.PopulationStats;
import com.bioinfo.cytoscope.fcs.Channel;
import com.bioinfo.cytoscope.fcs.FcsSample;
import com.bioinfo.cytoscope.gating.GatingResult;
import com.bioinfo.cytoscope.gating.Population;
import com.bioinfo.cytoscope.plot.Charts;
import com.bioinfo.cytoscope.plot.Density;
import com.bioinfo.cytoscope.scale.Scale;
import com.bioinfo.cytoscope.scale.ScaleType;
import com.bioinfo.cytoscope.scale.Scales;
import java.util.ArrayList;
import java.util.List;

/**
 * What the web application needs to draw a plot of one tube: the events counted on a grid (or a
 * histogram for one channel), the axes, and the statistics of every population of the tube.
 * The browser draws the gates itself, converting their values to positions with the axes.
 *
 * @param sampleId the tube
 * @param populationId the population whose events are drawn
 * @param events number of events drawn
 * @param x the horizontal axis
 * @param y the vertical axis; null for a histogram
 * @param density events counted on a grid; null for a histogram
 * @param histogram events counted along the horizontal axis; null for a density plot
 * @param populations statistics of every population of the tube
 */
public record PlotView(String sampleId, String populationId, int events, AxisView x, AxisView y,
                       Density density, int[] histogram, List<PopulationStats> populations) {

    /** Number of cells of the grid along each axis, by default. */
    public static final int DEFAULT_BINS = 128;

    /** Number of steps of the conversion table of an axis. */
    static final int STEPS = 256;

    /**
     * Asks for a plot.
     *
     * @param sampleId the tube
     * @param populationId the population whose events are drawn; the root if null
     * @param x horizontal channel
     * @param y vertical channel; null for a histogram
     * @param xScale scale of the horizontal axis; the channel default if null
     * @param yScale scale of the vertical axis; the channel default if null
     * @param bins cells of the grid along each axis; {@value #DEFAULT_BINS} if null
     */
    public record Request(String sampleId, String populationId, String x, String y,
                          ScaleType xScale, ScaleType yScale, Integer bins) {}

    /**
     * An axis of the plot.
     *
     * @param channel the channel
     * @param displayName name and stain, for the axis title
     * @param scale the scale used
     * @param ticks labeled ticks
     * @param values value at each position {@code i / 256}, {@code i} from 0 to 256, increasing:
     *     the browser interpolates in this table to convert between values and positions
     */
    public record AxisView(String channel, String displayName, ScaleType scale, List<Tick> ticks,
                           double[] values) {}

    /**
     * A labeled tick.
     *
     * @param value value of the tick
     * @param position position on the axis, 0 to 1
     * @param label text shown
     */
    public record Tick(double value, double position, String label) {}

    /**
     * Computes a plot.
     *
     * @param request what to draw
     * @param experiment the set-up, for the default scale of the DNA channel
     * @param gating the gated tube, compensated
     * @return the plot
     * @throws IllegalArgumentException if a channel or the population is unknown
     */
    public static PlotView of(Request request, Experiment experiment, GatingResult gating) {
        if (request.x() == null) {
            throw new IllegalArgumentException("Choose the channel of the horizontal axis");
        }
        String populationId = request.populationId() == null ? Population.ROOT_ID
                : request.populationId();
        if (gating.tree().find(populationId).isEmpty()) {
            throw new IllegalArgumentException("Unknown population " + populationId);
        }
        int bins = request.bins() == null ? DEFAULT_BINS : request.bins();
        if (bins < 8 || bins > 512) {
            throw new IllegalArgumentException("The grid needs 8 to 512 cells per axis");
        }
        String dna = experiment.cellCycle() == null ? null : experiment.cellCycle().dnaChannel();
        FcsSample sample = gating.sample();
        int xi = sample.requireChannel(request.x());
        double[] xValues = gating.values(populationId, xi);
        Scale xScale = scale(sample.channels().get(xi), request.xScale(), xValues, dna);
        AxisView xAxis = axis(sample.channels().get(xi), xScale);
        List<PopulationStats> stats = new ArrayList<>();
        for (Population population : gating.tree().root().flatten()) {
            String id = population.id();
            stats.add(new PopulationStats(id, gating.count(id), gating.percentOfParent(id),
                    gating.percentOfTotal(id)));
        }
        if (request.y() == null) {
            return new PlotView(request.sampleId(), populationId, xValues.length, xAxis, null,
                    null, Density.histogram(xValues, xScale, bins), stats);
        }
        int yi = sample.requireChannel(request.y());
        double[] yValues = gating.values(populationId, yi);
        Scale yScale = scale(sample.channels().get(yi), request.yScale(), yValues, dna);
        return new PlotView(request.sampleId(), populationId, xValues.length, xAxis,
                axis(sample.channels().get(yi), yScale),
                Density.of(xValues, yValues, xScale, yScale, bins), null, stats);
    }

    private static Scale scale(Channel channel, ScaleType type, double[] values, String dna) {
        return Scales.create(type == null ? Scales.defaultType(channel, dna) : type, channel,
                values);
    }

    private static AxisView axis(Channel channel, Scale scale) {
        List<Tick> ticks = new ArrayList<>();
        for (double t : scale.ticks()) {
            ticks.add(new Tick(t, scale.apply(t), Charts.tickLabel(t, scale)));
        }
        double[] values = new double[STEPS + 1];
        for (int i = 0; i <= STEPS; i++) {
            values[i] = scale.invert((double) i / STEPS);
        }
        return new AxisView(channel.name(), channel.displayName(), scale.type(), ticks, values);
    }
}
