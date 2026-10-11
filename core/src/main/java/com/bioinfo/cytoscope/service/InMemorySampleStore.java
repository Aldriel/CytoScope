package com.bioinfo.cytoscope.service;

import com.bioinfo.cytoscope.experiment.SampleStore;
import com.bioinfo.cytoscope.fcs.FcsSample;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Keeps the loaded tubes in memory for as long as the store lives, as the desktop does. */
public final class InMemorySampleStore implements SampleStore {

    private final Map<String, FcsSample> samples = new LinkedHashMap<>();

    /**
     * Adds or replaces a tube.
     *
     * @param id identifier of the tube
     * @param sample its values
     */
    public synchronized void put(String id, FcsSample sample) {
        samples.put(id, sample);
    }

    /**
     * Adds tubes.
     *
     * @param more tubes by identifier
     */
    public synchronized void putAll(Map<String, FcsSample> more) {
        samples.putAll(more);
    }

    /**
     * Removes a tube.
     *
     * @param id identifier of the tube
     */
    public synchronized void remove(String id) {
        samples.remove(id);
    }

    /** Removes every tube. */
    public synchronized void clear() {
        samples.clear();
    }

    @Override
    public synchronized Optional<FcsSample> find(String id) {
        return Optional.ofNullable(samples.get(id));
    }
}
