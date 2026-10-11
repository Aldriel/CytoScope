package com.bioinfo.cytoscope.service;

import com.bioinfo.cytoscope.compensation.Compensator;
import com.bioinfo.cytoscope.compensation.SpilloverMatrix;
import com.bioinfo.cytoscope.fcs.FcsSample;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Keeps the most recently compensated tubes, since every change of a gate re-analyzes all the
 * tubes with the same matrix. The least recently used entries leave first.
 */
final class CompensationCache {

    /** A tube, compared by identity since samples are immutable, and a matrix. */
    private record Key(FcsSample sample, SpilloverMatrix matrix) {

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && key.sample == sample && key.matrix.equals(matrix);
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(sample) + matrix.hashCode();
        }
    }

    private final Map<Key, FcsSample> entries;

    /**
     * Creates the cache.
     *
     * @param capacity number of compensated tubes kept
     */
    CompensationCache(int capacity) {
        entries = new LinkedHashMap<>(16, 0.75f, true) {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<Key, FcsSample> eldest) {
                return size() > capacity;
            }
        };
    }

    /**
     * Returns a compensated tube, computing it if needed.
     *
     * @param sample the measured values
     * @param matrix the spillover matrix
     * @return the compensated values
     */
    FcsSample compensate(FcsSample sample, SpilloverMatrix matrix) {
        Key key = new Key(sample, matrix);
        synchronized (entries) {
            FcsSample cached = entries.get(key);
            if (cached != null) {
                return cached;
            }
        }
        FcsSample compensated = Compensator.apply(sample, matrix);
        synchronized (entries) {
            entries.put(key, compensated);
        }
        return compensated;
    }

    /**
     * Returns the number of tubes kept.
     *
     * @return the size
     */
    int size() {
        synchronized (entries) {
            return entries.size();
        }
    }
}
