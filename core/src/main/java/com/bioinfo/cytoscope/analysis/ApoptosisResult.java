package com.bioinfo.cytoscope.analysis;

import java.util.Arrays;

/**
 * Number of cells of a sample in each state of the annexin V / PI test.
 */
public final class ApoptosisResult {

    private final String sampleName;
    private final int[] counts;

    /**
     * Creates the result.
     *
     * @param sampleName the sample
     * @param counts number of cells per state, in the order of {@link CellFate#values()}
     */
    public ApoptosisResult(String sampleName, int[] counts) {
        if (counts.length != CellFate.values().length) {
            throw new IllegalArgumentException("One count is needed per cell state");
        }
        this.sampleName = sampleName;
        this.counts = counts.clone();
    }

    /**
     * Returns the analyzed sample.
     *
     * @return its name
     */
    public String sampleName() {
        return sampleName;
    }

    /**
     * Returns the number of cells analyzed.
     *
     * @return the number of events in the parent population
     */
    public int total() {
        return Arrays.stream(counts).sum();
    }

    /**
     * Returns the number of cells in a state.
     *
     * @param fate the state
     * @return the count
     */
    public int count(CellFate fate) {
        return counts[fate.ordinal()];
    }

    /**
     * Returns the share of cells in a state.
     *
     * @param fate the state
     * @return the percentage of the cells analyzed, NaN if there is none
     */
    public double percent(CellFate fate) {
        int total = total();
        return total == 0 ? Double.NaN : 100.0 * counts[fate.ordinal()] / total;
    }

    /**
     * Returns the share of apoptotic cells, early and late.
     *
     * @return the percentage, NaN if there is no cell
     */
    public double percentApoptotic() {
        return percent(CellFate.EARLY_APOPTOTIC) + percent(CellFate.LATE_APOPTOTIC);
    }
}
