package com.bioinfo.cytoscope.server.workspace;

import com.bioinfo.cytoscope.experiment.Experiment;
import com.bioinfo.cytoscope.experiment.SampleStore;
import com.bioinfo.cytoscope.fcs.FcsSample;
import com.bioinfo.cytoscope.service.Example;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * The files and the set-up of one visitor of the web application, kept in memory for a while.
 * Nothing is written to disk.
 *
 * <p>Instances are thread-safe: a visitor may send several requests at once.
 */
public final class Workspace implements SampleStore {

    private final String id;
    private final Example example;
    private final String language;
    private final Map<String, FcsSample> samples = new LinkedHashMap<>();
    private Experiment experiment;
    private int nextSample = 1;

    /**
     * Creates a workspace.
     *
     * @param id identifier, hard to guess
     * @param example the example it was opened from, or null for the visitor's files
     * @param language language of the population names it creates, {@code en} or {@code fr}
     * @param experiment the initial set-up
     */
    Workspace(String id, Example example, String language, Experiment experiment) {
        this.id = Objects.requireNonNull(id, "id");
        this.example = example;
        this.language = language;
        this.experiment = Objects.requireNonNull(experiment, "experiment");
    }

    /**
     * Returns the identifier.
     *
     * @return the identifier
     */
    public String id() {
        return id;
    }

    /**
     * Returns the example the workspace was opened from.
     *
     * @return the example, empty for the visitor's own files
     */
    public Optional<Example> example() {
        return Optional.ofNullable(example);
    }

    /**
     * Returns the language of the names created by the workspace.
     *
     * @return {@code en} or {@code fr}
     */
    public String language() {
        return language;
    }

    /**
     * Returns the current set-up.
     *
     * @return the experiment
     */
    public synchronized Experiment experiment() {
        return experiment;
    }

    /**
     * Changes the set-up.
     *
     * @param change returns the new experiment from the current one
     * @return the new experiment
     */
    public synchronized Experiment update(UnaryOperator<Experiment> change) {
        experiment = Objects.requireNonNull(change.apply(experiment), "experiment");
        return experiment;
    }

    /**
     * Returns the loaded tubes.
     *
     * @return a copy of the tubes, by identifier, in loading order
     */
    public synchronized Map<String, FcsSample> samples() {
        return new LinkedHashMap<>(samples);
    }

    @Override
    public synchronized Optional<FcsSample> find(String sampleId) {
        return Optional.ofNullable(samples.get(sampleId));
    }

    /**
     * Returns a new tube identifier, not used by a loaded tube.
     *
     * @return for example {@code s3}
     */
    public synchronized String nextSampleId() {
        String id;
        do {
            id = "s" + nextSample++;
        } while (samples.containsKey(id));
        return id;
    }

    synchronized void put(String sampleId, FcsSample sample) {
        samples.put(sampleId, sample);
    }

    /**
     * Removes a tube from the workspace and from the set-up.
     *
     * @param sampleId identifier of the tube
     * @return true if the tube was loaded
     */
    public synchronized boolean remove(String sampleId) {
        experiment = experiment.withoutSample(sampleId);
        return samples.remove(sampleId) != null;
    }

    /**
     * Returns the memory the tubes take, with room for a compensated copy of each.
     *
     * @return the size, in bytes
     */
    public synchronized long bytes() {
        return samples.values().stream().mapToLong(Workspace::footprint).sum();
    }

    /**
     * Returns the memory a tube takes, with room for its compensated copy.
     *
     * @param sample the tube
     * @return the size, in bytes
     */
    static long footprint(FcsSample sample) {
        return 2L * sample.eventCount() * sample.channels().size() * Float.BYTES;
    }
}
