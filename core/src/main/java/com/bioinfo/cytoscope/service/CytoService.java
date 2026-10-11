package com.bioinfo.cytoscope.service;

import com.bioinfo.cytoscope.analysis.ApoptosisAnalysis;
import com.bioinfo.cytoscope.analysis.ApoptosisSettings;
import com.bioinfo.cytoscope.compensation.SpilloverEstimator;
import com.bioinfo.cytoscope.compensation.SpilloverMatrix;
import com.bioinfo.cytoscope.experiment.CompensationSource;
import com.bioinfo.cytoscope.experiment.Experiment;
import com.bioinfo.cytoscope.experiment.ExperimentAnalyzer;
import com.bioinfo.cytoscope.experiment.ExperimentResults;
import com.bioinfo.cytoscope.experiment.SampleAnnotator;
import com.bioinfo.cytoscope.experiment.SampleInfo;
import com.bioinfo.cytoscope.experiment.SampleRole;
import com.bioinfo.cytoscope.experiment.SampleStore;
import com.bioinfo.cytoscope.fcs.FcsReader;
import com.bioinfo.cytoscope.fcs.FcsSample;
import com.bioinfo.cytoscope.gating.GatingEngine;
import com.bioinfo.cytoscope.gating.GatingResult;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Entry point of the analyses, shared by the desktop application and the web server: reading
 * the files, setting up the experiment, compensating, gating and analyzing.
 *
 * <p>The service keeps no experiment: each call receives the set-up and the store of the tubes.
 * It only keeps the recently compensated tubes, which the calls share safely.
 */
public final class CytoService {

    /** Number of compensated tubes kept by default. */
    public static final int DEFAULT_CACHE_CAPACITY = 64;

    private static final Pattern CONTROL_GROUP = Pattern.compile(
            "NC|CTRL|CONTROL|CONTR[OÔ]LE|T[EÉ]MOIN|UNTREATED|NON TRAIT[EÉ]|VEHICLE|V[EÉ]HICULE"
                    + "|DMSO|PBS|0(?:[.,]0+)?(?:\\s*\\S+)?");

    private final CompensationCache cache;

    /** Creates the service with the default cache. */
    public CytoService() {
        this(DEFAULT_CACHE_CAPACITY);
    }

    /**
     * Creates the service.
     *
     * @param cacheCapacity number of compensated tubes kept
     */
    public CytoService(int cacheCapacity) {
        cache = new CompensationCache(cacheCapacity);
    }

    /**
     * Reads an FCS file.
     *
     * @param fileName name of the file, with or without extension
     * @param bytes content of the file
     * @return the sample, named after the file without extension
     * @throws com.bioinfo.cytoscope.fcs.FcsFormatException if the file is not valid FCS
     */
    public FcsSample read(String fileName, byte[] bytes) {
        String name = fileName.replaceAll("(?i)\\.(fcs|lmd)$", "");
        return FcsReader.read(name, bytes);
    }

    /**
     * Adds a tube to an experiment, with the role, group and dose guessed from the file. The
     * first tube that carries a spillover matrix turns the compensation on, and the first group
     * that looks like a control becomes the reference of the comparison.
     *
     * @param experiment the experiment
     * @param id identifier of the tube in the store
     * @param sample the values of the tube
     * @return the new experiment
     */
    public Experiment addSample(Experiment experiment, String id, FcsSample sample) {
        SampleInfo info = SampleAnnotator.guess(id, sample);
        Experiment result = experiment.withSample(info);
        if (result.compensation() == CompensationSource.NONE) {
            Optional<SpilloverMatrix> matrix = SpilloverMatrix.fromSample(sample);
            if (matrix.isPresent()) {
                result = result.withCompensation(CompensationSource.FILE, matrix.get());
            }
        }
        if (result.controlGroup().isEmpty() && info.isCompared() && looksLikeControl(info)) {
            result = result.withComparison(result.metric(), info.group());
        }
        return result;
    }

    private static boolean looksLikeControl(SampleInfo info) {
        return info.dose() == 0
                || CONTROL_GROUP.matcher(info.group().trim().toUpperCase(Locale.ROOT)).matches();
    }

    /**
     * Returns the values of a tube ready to gate, compensated as the experiment says.
     *
     * @param store the tubes
     * @param experiment the set-up
     * @param id identifier of the tube
     * @return the values
     * @throws com.bioinfo.cytoscope.experiment.MissingSampleException if the tube is gone
     */
    public FcsSample prepared(SampleStore store, Experiment experiment, String id) {
        FcsSample sample = store.require(id);
        return experiment.matrix() == null ? sample : cache.compensate(sample, experiment.matrix());
    }

    /**
     * Gates one tube.
     *
     * @param store the tubes
     * @param experiment the set-up
     * @param id identifier of the tube
     * @return the events of each population
     */
    public GatingResult gate(SampleStore store, Experiment experiment, String id) {
        return GatingEngine.apply(prepared(store, experiment, id), experiment.gating());
    }

    /**
     * Runs every analysis of the experiment.
     *
     * @param store the tubes
     * @param experiment the set-up
     * @return the results
     */
    public ExperimentResults analyze(SampleStore store, Experiment experiment) {
        return ExperimentAnalyzer.analyze(experiment, id -> prepared(store, experiment, id));
    }

    /**
     * Computes the spillover matrix from the single-stain controls of the experiment, with the
     * unstained control as negative reference when there is one.
     *
     * @param store the tubes
     * @param experiment the set-up, whose single stains name their channel
     * @return the matrix
     * @throws IllegalArgumentException if there are fewer than two single stains, or if a
     *     control is too dim
     */
    public SpilloverMatrix matrixFromControls(SampleStore store, Experiment experiment) {
        Map<String, FcsSample> controls = new LinkedHashMap<>();
        FcsSample unstained = null;
        for (SampleInfo info : experiment.samples()) {
            if (info.role() == SampleRole.SINGLE_STAIN) {
                FcsSample sample = store.require(info.id());
                String channel = sample.channels()
                        .get(sample.requireChannel(info.stainChannel())).name();
                controls.putIfAbsent(channel, sample);
            } else if (info.role() == SampleRole.UNSTAINED && unstained == null) {
                unstained = store.require(info.id());
            }
        }
        if (controls.size() < 2) {
            throw new IllegalArgumentException(
                    "Two single-stain controls, at least, are needed to compute the matrix");
        }
        return SpilloverEstimator.estimate(controls, unstained);
    }

    /**
     * Proposes the thresholds of the annexin V / PI test from the controls: the annexin V
     * threshold from the PI single stain, the PI threshold from the annexin V single stain, and
     * the unstained control for a missing single stain. The cells are taken in the analyzed
     * population, after compensation.
     *
     * @param store the tubes
     * @param experiment the set-up
     * @param settings population and channels of the test
     * @return the settings with the proposed thresholds; a threshold stays as it was when no
     *     control can propose it
     */
    public ApoptosisSettings suggestThresholds(SampleStore store, Experiment experiment,
                                               ApoptosisSettings settings) {
        double annexin = thresholdFrom(store, experiment, settings, settings.piChannel(),
                settings.annexinChannel());
        double pi = thresholdFrom(store, experiment, settings, settings.annexinChannel(),
                settings.piChannel());
        return settings.withThresholds(
                Double.isNaN(annexin) ? settings.annexinThreshold() : annexin,
                Double.isNaN(pi) ? settings.piThreshold() : pi);
    }

    /** Threshold of a channel from the control stained only with the other dye. */
    private double thresholdFrom(SampleStore store, Experiment experiment,
                                 ApoptosisSettings settings, String otherDye, String channel) {
        SampleInfo control = null;
        SampleInfo unstained = null;
        for (SampleInfo info : experiment.samples()) {
            if (info.role() == SampleRole.SINGLE_STAIN && control == null
                    && sameChannel(store, info, otherDye)) {
                control = info;
            } else if (info.role() == SampleRole.UNSTAINED && unstained == null) {
                unstained = info;
            }
        }
        SampleInfo chosen = control != null ? control : unstained;
        if (chosen == null) {
            return Double.NaN;
        }
        GatingResult gating = gate(store, experiment, chosen.id());
        if (gating.tree().find(settings.parentId()).isEmpty()) {
            return Double.NaN;
        }
        double[] values = gating.values(settings.parentId(),
                gating.sample().requireChannel(channel));
        return ApoptosisAnalysis.thresholdFromControl(values);
    }

    private static boolean sameChannel(SampleStore store, SampleInfo info, String channel) {
        FcsSample sample = store.require(info.id());
        int stain = sample.indexOf(info.stainChannel());
        return stain >= 0 && stain == sample.indexOf(channel);
    }
}
