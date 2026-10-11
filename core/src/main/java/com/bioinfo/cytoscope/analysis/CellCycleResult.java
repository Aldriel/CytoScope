package com.bioinfo.cytoscope.analysis;

import java.util.Arrays;

/**
 * Distribution of the cells of a sample over the phases of the cell cycle.
 *
 * @param sampleName the sample
 * @param g1Peak DNA value of the G0/G1 peak
 * @param g2Peak DNA value of the G2/M peak
 * @param g1Cv robust coefficient of variation of the G0/G1 peak, in percent: below 5 % the
 *     staining is good, above 8 % the phases overlap
 * @param bounds the four values that separate the five phases, in increasing order
 * @param counts number of cells per phase, in the order of {@link CellCyclePhase#values()}
 */
public record CellCycleResult(String sampleName, double g1Peak, double g2Peak, double g1Cv,
                              double[] bounds, int[] counts) {

    /** Creates the result and copies its arrays. */
    public CellCycleResult {
        if (bounds.length != CellCyclePhase.values().length - 1
                || counts.length != CellCyclePhase.values().length) {
            throw new IllegalArgumentException("One count per phase and four bounds are needed");
        }
        bounds = bounds.clone();
        counts = counts.clone();
    }

    @Override
    public double[] bounds() {
        return bounds.clone();
    }

    @Override
    public int[] counts() {
        return counts.clone();
    }

    /**
     * Returns the number of cells analyzed.
     *
     * @return the total of the phases
     */
    public int total() {
        return Arrays.stream(counts).sum();
    }

    /**
     * Returns the share of cells in a phase.
     *
     * @param phase the phase
     * @return the percentage of the cells analyzed, NaN if there is none
     */
    public double percent(CellCyclePhase phase) {
        int total = total();
        return total == 0 ? Double.NaN : 100.0 * counts[phase.ordinal()] / total;
    }

    /**
     * Returns the ratio of the G2/M peak to the G0/G1 peak, close to 2 for a linear instrument.
     *
     * @return the ratio
     */
    public double g2g1Ratio() {
        return g2Peak / g1Peak;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CellCycleResult that && sampleName.equals(that.sampleName)
                && g1Peak == that.g1Peak && g2Peak == that.g2Peak && g1Cv == that.g1Cv
                && Arrays.equals(bounds, that.bounds) && Arrays.equals(counts, that.counts);
    }

    @Override
    public int hashCode() {
        return 31 * sampleName.hashCode() + Arrays.hashCode(counts);
    }

    @Override
    public String toString() {
        return "CellCycleResult[" + sampleName + ", G1 " + g1Peak + ", counts "
                + Arrays.toString(counts) + "]";
    }
}
