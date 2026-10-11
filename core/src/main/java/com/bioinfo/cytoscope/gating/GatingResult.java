package com.bioinfo.cytoscope.gating;

import com.bioinfo.cytoscope.fcs.FcsSample;
import com.bioinfo.cytoscope.stats.Descriptive;
import java.util.BitSet;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The events of each population of a sample, after gating.
 */
public final class GatingResult {

    private final FcsSample sample;
    private final GatingTree tree;
    private final Map<String, BitSet> events;

    GatingResult(FcsSample sample, GatingTree tree, Map<String, BitSet> events) {
        this.sample = sample;
        this.tree = tree;
        this.events = Collections.unmodifiableMap(new LinkedHashMap<>(events));
    }

    /**
     * Returns the gated sample.
     *
     * @return the sample
     */
    public FcsSample sample() {
        return sample;
    }

    /**
     * Returns the hierarchy that was applied.
     *
     * @return the gating tree
     */
    public GatingTree tree() {
        return tree;
    }

    /**
     * Returns the events of a population.
     *
     * @param populationId identifier of the population
     * @return a copy of the set of event indices
     * @throws IllegalArgumentException if the population is unknown
     */
    public BitSet events(String populationId) {
        return (BitSet) require(populationId).clone();
    }

    /**
     * Returns the number of events of a population.
     *
     * @param populationId identifier of the population
     * @return the count
     */
    public int count(String populationId) {
        return require(populationId).cardinality();
    }

    /**
     * Returns the share of the parent events that a population holds.
     *
     * @param populationId identifier of the population
     * @return the percentage, 100 for the root, NaN if the parent is empty
     */
    public double percentOfParent(String populationId) {
        String parent = tree.root().parentOf(populationId).map(Population::id).orElse(null);
        if (parent == null) {
            require(populationId);
            return 100;
        }
        int parentCount = count(parent);
        return parentCount == 0 ? Double.NaN : 100.0 * count(populationId) / parentCount;
    }

    /**
     * Returns the share of all the events that a population holds.
     *
     * @param populationId identifier of the population
     * @return the percentage, NaN if the sample has no event
     */
    public double percentOfTotal(String populationId) {
        int total = sample.eventCount();
        return total == 0 ? Double.NaN : 100.0 * count(populationId) / total;
    }

    /**
     * Returns the values of one channel for the events of a population.
     *
     * @param populationId identifier of the population
     * @param channel index of the channel
     * @return the values, in event order
     */
    public double[] values(String populationId, int channel) {
        BitSet selected = require(populationId);
        double[] values = new double[selected.cardinality()];
        int i = 0;
        for (int e = selected.nextSetBit(0); e >= 0; e = selected.nextSetBit(e + 1)) {
            values[i++] = sample.value(channel, e);
        }
        return values;
    }

    /**
     * Returns the median of a channel in a population, the usual measure of its intensity.
     *
     * @param populationId identifier of the population
     * @param channel index of the channel
     * @return the median, NaN if the population is empty
     */
    public double median(String populationId, int channel) {
        return Descriptive.median(values(populationId, channel));
    }

    private BitSet require(String populationId) {
        BitSet selected = events.get(populationId);
        if (selected == null) {
            throw new IllegalArgumentException("Unknown population: " + populationId);
        }
        return selected;
    }
}
