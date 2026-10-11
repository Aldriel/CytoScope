package com.bioinfo.cytoscope.experiment;

import com.bioinfo.cytoscope.analysis.ApoptosisSettings;
import com.bioinfo.cytoscope.fcs.Channel;
import com.bioinfo.cytoscope.fcs.FcsSample;
import com.bioinfo.cytoscope.gating.GatingTree;
import com.bioinfo.cytoscope.gating.IntervalGate;
import java.util.List;
import java.util.Map;

/** Small annexin V / PI tubes for the tests. */
public final class Tubes {

    /** Channels of the test tubes. */
    public static final List<Channel> CHANNELS = List.of(new Channel("FSC-A", "", 1000),
            new Channel("FITC-A", "Annexin V", 1000), new Channel("PI-A", "", 1000));

    private Tubes() {}

    /**
     * Creates a tube of 100 cells and one debris event.
     *
     * @param name sample name
     * @param apoptotic number of early apoptotic cells (annexin V 500, PI 50); the others are
     *     live (10, 10)
     * @param keywords keywords of the tube
     * @return the tube
     */
    public static FcsSample tube(String name, int apoptotic, Map<String, String> keywords) {
        float[][] values = new float[3][101];
        for (int i = 0; i < 100; i++) {
            values[0][i] = 500;
            values[1][i] = i < apoptotic ? 500 : 10;
            values[2][i] = i < apoptotic ? 50 : 10;
        }
        values[0][100] = 50;
        return new FcsSample(name, keywords, CHANNELS, values);
    }

    /**
     * Returns the gating used with the test tubes: {@code p1} Cells, FSC-A 100 to 1000.
     *
     * @return the tree
     */
    public static GatingTree gating() {
        return GatingTree.create("All").add("all", "Cells", new IntervalGate("FSC-A", 100, 1000));
    }

    /**
     * Returns the annexin V / PI settings used with the test tubes.
     *
     * @return thresholds at 100 on both dyes, on the Cells population
     */
    public static ApoptosisSettings apoptosis() {
        return new ApoptosisSettings("p1", "FITC-A", "PI-A", 100, 100);
    }
}
