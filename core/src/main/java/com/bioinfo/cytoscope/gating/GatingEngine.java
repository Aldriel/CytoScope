package com.bioinfo.cytoscope.gating;

import com.bioinfo.cytoscope.fcs.FcsSample;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Applies a gating hierarchy to a sample: each population keeps the events of its parent that
 * fall inside its gate.
 */
public final class GatingEngine {

    private GatingEngine() {}

    /**
     * Gates a sample.
     *
     * @param sample the sample, compensated if needed
     * @param tree the hierarchy of gates
     * @return the events of every population
     * @throws IllegalArgumentException if a gate uses a channel the sample does not have
     */
    public static GatingResult apply(FcsSample sample, GatingTree tree) {
        Map<String, BitSet> events = new LinkedHashMap<>();
        BitSet all = new BitSet(sample.eventCount());
        all.set(0, sample.eventCount());
        events.put(tree.root().id(), all);
        for (Population child : tree.root().children()) {
            gate(sample, child, all, events);
        }
        return new GatingResult(sample, tree, events);
    }

    private static void gate(FcsSample sample, Population population, BitSet parent,
                             Map<String, BitSet> events) {
        Gate gate = population.gate();
        int x = sample.requireChannel(gate.xChannel());
        int y = gate.yChannel() == null ? -1 : sample.requireChannel(gate.yChannel());
        BitSet selected = new BitSet(sample.eventCount());
        for (int e = parent.nextSetBit(0); e >= 0; e = parent.nextSetBit(e + 1)) {
            double yValue = y < 0 ? 0 : sample.value(y, e);
            if (gate.contains(sample.value(x, e), yValue)) {
                selected.set(e);
            }
        }
        events.put(population.id(), selected);
        for (Population child : population.children()) {
            gate(sample, child, selected, events);
        }
    }
}
