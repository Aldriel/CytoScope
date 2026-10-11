package com.bioinfo.cytoscope.analysis;

import com.bioinfo.cytoscope.fcs.FcsSample;
import com.bioinfo.cytoscope.gating.GatingResult;
import com.bioinfo.cytoscope.stats.Descriptive;
import java.util.BitSet;

/**
 * Reads the annexin V / propidium iodide test: places each cell of a population in one of four
 * states, from two thresholds.
 */
public final class ApoptosisAnalysis {

    /**
     * Percentile of a control without the stain above which a cell counts as positive: 1 % of
     * the unstained cells are then false positives, the usual compromise.
     */
    public static final double CONTROL_PERCENTILE = 99;

    private ApoptosisAnalysis() {}

    /**
     * Analyzes a gated sample.
     *
     * @param gating the gated sample
     * @param settings population, channels and thresholds
     * @return the number of cells in each state
     * @throws IllegalArgumentException if the population or a channel does not exist
     */
    public static ApoptosisResult analyze(GatingResult gating, ApoptosisSettings settings) {
        FcsSample sample = gating.sample();
        int annexin = sample.requireChannel(settings.annexinChannel());
        int pi = sample.requireChannel(settings.piChannel());
        BitSet cells = gating.events(settings.parentId());
        int[] counts = new int[CellFate.values().length];
        for (int e = cells.nextSetBit(0); e >= 0; e = cells.nextSetBit(e + 1)) {
            CellFate fate = CellFate.of(sample.value(annexin, e) >= settings.annexinThreshold(),
                    sample.value(pi, e) >= settings.piThreshold());
            counts[fate.ordinal()]++;
        }
        return new ApoptosisResult(sample.name(), counts);
    }

    /**
     * Proposes a threshold from a control that lacks the stain: the value that 99 % of its cells
     * stay below. The best control is the single stain of the other dye, which also shows the
     * spread left by compensation; the unstained control is the fallback.
     *
     * @param controlValues values of the channel in the cells of the control
     * @return the threshold, NaN if the control has no cell
     */
    public static double thresholdFromControl(double[] controlValues) {
        return Descriptive.percentile(controlValues, CONTROL_PERCENTILE);
    }
}
