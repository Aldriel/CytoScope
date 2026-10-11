package com.bioinfo.cytoscope.desktop.model;

import com.bioinfo.cytoscope.analysis.ApoptosisSettings;
import com.bioinfo.cytoscope.compensation.SpilloverMatrix;
import com.bioinfo.cytoscope.experiment.CompensationSource;
import com.bioinfo.cytoscope.experiment.Experiment;
import com.bioinfo.cytoscope.experiment.ExperimentResults;
import com.bioinfo.cytoscope.experiment.SampleInfo;
import com.bioinfo.cytoscope.fcs.Channel;
import com.bioinfo.cytoscope.fcs.FcsSample;
import com.bioinfo.cytoscope.fcs.FcsWriter;
import com.bioinfo.cytoscope.gating.GatingResult;
import com.bioinfo.cytoscope.gating.Population;
import com.bioinfo.cytoscope.report.CytoReport;
import com.bioinfo.cytoscope.report.Figure;
import com.bioinfo.cytoscope.report.PdfReportWriter;
import com.bioinfo.cytoscope.report.ReportFigures;
import com.bioinfo.cytoscope.report.ReportTexts;
import com.bioinfo.cytoscope.service.CytoService;
import com.bioinfo.cytoscope.service.Example;
import com.bioinfo.cytoscope.service.ExampleLibrary;
import com.bioinfo.cytoscope.service.InMemorySampleStore;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * State of the desktop application (the Model of the MVC pattern): the tubes loaded, the set-up
 * of the experiment and the last results. The controllers bind the views to its observable
 * properties and call its operations.
 *
 * <p>The work is delegated to {@link CytoService}. Reading files, computing a matrix, analyzing
 * and writing files run on the {@code background} executor and publish their results on the
 * {@code uiThread} executor, so that the window stays responsive; the tests pass executors running
 * everything immediately. Gating one tube for a plot is fast and runs on the calling thread.
 */
public final class DesktopModel {

    private final CytoService service;
    private final ExampleLibrary library;
    private final Executor background;
    private final Executor uiThread;
    private final String appVersion;
    private final InMemorySampleStore store = new InMemorySampleStore();
    /** Values of the tubes, in loading order, read on the UI thread only. */
    private final Map<String, FcsSample> samples = new LinkedHashMap<>();

    private final ReadOnlyObjectWrapper<Experiment> experiment;
    private final ReadOnlyObjectWrapper<Example> example = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<List<Channel>> channels =
            new ReadOnlyObjectWrapper<>(List.of());
    private final ReadOnlyObjectWrapper<ExperimentResults> results = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyBooleanWrapper resultsFresh = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyObjectWrapper<StatusMessage> status =
            new ReadOnlyObjectWrapper<>(StatusMessage.READY);
    private final ReadOnlyBooleanWrapper busy = new ReadOnlyBooleanWrapper(false);
    private int runningTasks;
    private int nextSample;

    /**
     * Creates the model, with an empty experiment.
     *
     * @param service the analyses
     * @param background runs the long operations
     * @param uiThread runs the updates of the observable state (the JavaFX application thread)
     * @param appVersion version of the application, printed in the reports
     * @param rootName name of the population of all events, in the language of the user
     */
    public DesktopModel(
            CytoService service,
            Executor background,
            Executor uiThread,
            String appVersion,
            String rootName) {
        this.service = Objects.requireNonNull(service, "service");
        this.library = new ExampleLibrary(service);
        this.background = Objects.requireNonNull(background, "background");
        this.uiThread = Objects.requireNonNull(uiThread, "uiThread");
        this.appVersion = Objects.requireNonNull(appVersion, "appVersion");
        experiment = new ReadOnlyObjectWrapper<>(Experiment.empty(rootName));
        experiment.addListener((obs, old, value) -> resultsFresh.set(false));
    }

    // ----- Tubes -----

    /**
     * Forgets the tubes and starts an empty experiment.
     *
     * @param rootName name of the population of all events, in the language of the user
     */
    public void newExperiment(String rootName) {
        replace(Experiment.empty(rootName), Map.of(), null);
        status.set(StatusMessage.READY);
    }

    /**
     * Reads FCS files and adds them to the experiment, with the role, group and dose of each tube
     * guessed from its file. Files added to an example start a new experiment.
     *
     * @param files the files
     * @param rootName name of the population of all events, used when a new experiment starts
     */
    public void openFiles(List<Path> files, String rootName) {
        if (files.isEmpty()) {
            return;
        }
        List<Path> chosen = List.copyOf(files);
        runAsync(
                StatusMessage.info("status.reading", chosen.size()),
                () -> {
                    List<FcsSample> read = new ArrayList<>();
                    for (Path file : chosen) {
                        read.add(
                                service.read(
                                        file.getFileName().toString(), Files.readAllBytes(file)));
                    }
                    return read;
                },
                read -> {
                    if (example.get() != null) {
                        replace(Experiment.empty(rootName), Map.of(), null);
                    }
                    Experiment updated = experiment.get();
                    for (FcsSample sample : read) {
                        String id = "s" + ++nextSample;
                        samples.put(id, sample);
                        store.put(id, sample);
                        updated = service.addSample(updated, id, sample);
                    }
                    updateChannels();
                    experiment.set(updated);
                    status.set(
                            StatusMessage.info(
                                    "status.filesOpened", read.size(), samples.size()));
                });
    }

    /**
     * Opens an example experiment, already set up.
     *
     * @param chosen the example
     * @param rootName name of the population of all events, in the language of the user
     * @param names names of the populations created by the preset, in the language of the user
     */
    public void openExample(Example chosen, String rootName, ExampleLibrary.PresetNames names) {
        int[] next = {nextSample};
        runAsync(
                StatusMessage.info("status.openingExample"),
                () -> library.open(chosen, rootName, names, () -> "s" + ++next[0]),
                opened -> {
                    nextSample = next[0];
                    replace(opened.experiment(), opened.samples(), chosen);
                    status.set(
                            StatusMessage.info(
                                    "status.opened." + chosen.name(), opened.samples().size()));
                });
    }

    /**
     * Removes a tube from the experiment.
     *
     * @param id identifier of the tube
     */
    public void removeSample(String id) {
        Optional<SampleInfo> info = experiment.get().sample(id);
        if (info.isEmpty()) {
            return;
        }
        samples.remove(id);
        store.remove(id);
        updateChannels();
        experiment.set(experiment.get().withoutSample(id));
        status.set(StatusMessage.info("status.tubeRemoved", info.get().name()));
    }

    private void replace(
            Experiment newExperiment, Map<String, FcsSample> newSamples, Example from) {
        samples.clear();
        samples.putAll(newSamples);
        store.clear();
        store.putAll(newSamples);
        example.set(from);
        results.set(null);
        updateChannels();
        experiment.set(newExperiment);
    }

    /**
     * The channels of the experiment are those of its first tube. They change before the
     * experiment, so that the views drawing the new experiment find its channels.
     */
    private void updateChannels() {
        channels.set(
                samples.isEmpty() ? List.of() : samples.values().iterator().next().channels());
    }

    /**
     * Returns the number of events of a tube.
     *
     * @param id identifier of the tube
     * @return the number of events, 0 if the tube is unknown
     */
    public int eventCount(String id) {
        FcsSample sample = samples.get(id);
        return sample == null ? 0 : sample.eventCount();
    }

    // ----- Set-up -----

    /**
     * Changes the set-up of the experiment. The analyses and the compared value that refer to a
     * population no longer in the tree are dropped; an invalid change is reported in the status
     * bar and leaves the experiment as it was.
     *
     * @param change returns the new experiment
     * @return whether the change was applied
     */
    public boolean update(UnaryOperator<Experiment> change) {
        try {
            Experiment changed = change.apply(experiment.get());
            experiment.set(ExperimentSetup.withoutDanglingReferences(changed));
            return true;
        } catch (IllegalArgumentException e) {
            fail(e);
            return false;
        }
    }

    /** Computes the spillover matrix from the single-stain controls. */
    public void computeMatrix() {
        Experiment current = experiment.get();
        runAsync(
                StatusMessage.info("status.computingMatrix"),
                () -> service.matrixFromControls(store, current),
                matrix -> {
                    update(e -> e.withCompensation(CompensationSource.CONTROLS, matrix));
                    status.set(
                            StatusMessage.info(
                                    "status.matrixComputed", matrix.channels().size()));
                });
    }

    /**
     * Returns the spillover matrix written in the files by the cytometer: that of the first tube
     * that has one.
     *
     * @return the matrix, if a tube has one
     */
    public Optional<SpilloverMatrix> fileMatrix() {
        return samples.values().stream()
                .map(SpilloverMatrix::fromSample)
                .flatMap(Optional::stream)
                .findFirst();
    }

    /** Compensates with the matrix written in the files, if a tube has one. */
    public void useFileMatrix() {
        Optional<SpilloverMatrix> matrix = fileMatrix();
        if (matrix.isEmpty()) {
            status.set(StatusMessage.error("error.noFileMatrix"));
            return;
        }
        update(e -> e.withCompensation(CompensationSource.FILE, matrix.get()));
    }

    /** Proposes the thresholds of the annexin V / PI test from the controls. */
    public void suggestThresholds() {
        Experiment current = experiment.get();
        ApoptosisSettings settings = current.apoptosis();
        if (settings == null) {
            return;
        }
        runAsync(
                StatusMessage.info("status.suggesting"),
                () -> service.suggestThresholds(store, current, settings),
                suggested -> {
                    update(e -> e.withApoptosis(suggested));
                    status.set(StatusMessage.info("status.thresholdsSuggested"));
                });
    }

    // ----- Gating and results -----

    /**
     * Gates one tube with the current set-up, for a plot.
     *
     * @param sampleId identifier of the tube
     * @return the events of each population, or empty if the tube cannot be gated (the reason is
     *     shown in the status bar)
     */
    public Optional<GatingResult> gate(String sampleId) {
        try {
            return Optional.of(service.gate(store, experiment.get(), sampleId));
        } catch (RuntimeException e) {
            fail(e);
            return Optional.empty();
        }
    }

    /** Runs every analysis of the experiment, unless the results are up to date. */
    public void analyze() {
        Experiment current = experiment.get();
        if (resultsFresh.get() || current.samples().isEmpty()) {
            return;
        }
        runAsync(
                StatusMessage.info("status.analyzing"),
                () -> service.analyze(store, current),
                computed -> {
                    if (experiment.get() == current) {
                        results.set(computed);
                        resultsFresh.set(true);
                        status.set(StatusMessage.info("status.analyzed"));
                    }
                });
    }

    /**
     * Writes the events of a population of one tube as an FCS 3.1 file.
     *
     * @param sampleId the tube
     * @param populationId the population
     * @param file the file to write
     */
    public void exportPopulation(String sampleId, String populationId, Path file) {
        Experiment current = experiment.get();
        runAsync(
                StatusMessage.info("status.exporting"),
                () -> {
                    GatingResult gating = service.gate(store, current, sampleId);
                    Population population =
                            gating.tree()
                                    .find(populationId)
                                    .orElseThrow(
                                            () ->
                                                    new IllegalArgumentException(
                                                            "Unknown population " + populationId));
                    FcsSample raw = store.require(sampleId);
                    String name =
                            population.id().equals(Population.ROOT_ID)
                                    ? raw.name()
                                    : raw.name() + " - " + population.name();
                    Files.write(
                            file, FcsWriter.write(raw.withName(name), gating.events(populationId)));
                    return gating.count(populationId);
                },
                count ->
                        status.set(
                                StatusMessage.info(
                                        "status.exported", count, file.getFileName())));
    }

    /**
     * Writes the PDF report of the experiment.
     *
     * @param file the PDF file to write
     * @param options title, language, analyst and notes
     * @param onSaved called on the UI thread once the file is written
     */
    public void writeReport(Path file, ReportOptions options, Consumer<Path> onSaved) {
        Experiment current = experiment.get();
        if (current.samples().isEmpty()) {
            status.set(StatusMessage.error("error.noTubes"));
            return;
        }
        Example source = example.get();
        runAsync(
                StatusMessage.info("status.writingReport"),
                () -> {
                    ExperimentResults computed = service.analyze(store, current);
                    ZoneId zone = ZoneId.systemDefault();
                    ReportTexts texts = new ReportTexts(options.locale(), zone);
                    List<Figure> figures =
                            ReportFigures.of(
                                    current,
                                    computed,
                                    id -> service.gate(store, current, id),
                                    texts);
                    CytoReport report =
                            CytoReport.builder(current, computed)
                                    .title(options.title())
                                    .analyst(options.analyst())
                                    .notes(options.notes())
                                    .source(sourceText(source, texts))
                                    .locale(options.locale(), zone)
                                    .generatedAt(Instant.now())
                                    .appVersion(appVersion)
                                    .figures(figures)
                                    .build();
                    try (OutputStream out = Files.newOutputStream(file)) {
                        new PdfReportWriter().write(report, out);
                    }
                    return file;
                },
                saved -> {
                    status.set(StatusMessage.info("status.reportSaved", saved.getFileName()));
                    onSaved.accept(saved);
                });
    }

    private static String sourceText(Example source, ReportTexts texts) {
        if (source == null) {
            return null;
        }
        return texts.get(source.simulated() ? "source.simulated" : "source.mendeley");
    }

    // ----- Background tasks -----

    private <T> void runAsync(StatusMessage progress, Callable<T> work, Consumer<T> onSuccess) {
        runningTasks++;
        busy.set(true);
        status.set(progress);
        background.execute(
                () -> {
                    try {
                        T result = work.call();
                        uiThread.execute(
                                () -> {
                                    endTask();
                                    onSuccess.accept(result);
                                });
                    } catch (Exception e) {
                        uiThread.execute(
                                () -> {
                                    endTask();
                                    fail(e);
                                });
                    }
                });
    }

    private void endTask() {
        runningTasks--;
        busy.set(runningTasks > 0);
    }

    private void fail(Exception e) {
        status.set(StatusMessage.fromException(e));
    }

    // ----- Observable state -----

    /** Returns the set-up of the experiment. */
    public ReadOnlyObjectProperty<Experiment> experimentProperty() {
        return experiment.getReadOnlyProperty();
    }

    /** Returns the example opened, or {@code null} for the files of the user. */
    public ReadOnlyObjectProperty<Example> exampleProperty() {
        return example.getReadOnlyProperty();
    }

    /** Returns the channels of the tubes, those of the first tube; empty without tubes. */
    public ReadOnlyObjectProperty<List<Channel>> channelsProperty() {
        return channels.getReadOnlyProperty();
    }

    /** Returns the last results, or {@code null}; they may be older than the set-up. */
    public ReadOnlyObjectProperty<ExperimentResults> resultsProperty() {
        return results.getReadOnlyProperty();
    }

    /** Returns whether the results were computed with the current set-up. */
    public ReadOnlyBooleanProperty resultsFreshProperty() {
        return resultsFresh.getReadOnlyProperty();
    }

    /** Returns the message shown in the status bar. */
    public ReadOnlyObjectProperty<StatusMessage> statusProperty() {
        return status.getReadOnlyProperty();
    }

    /** Returns whether a background operation is running. */
    public ReadOnlyBooleanProperty busyProperty() {
        return busy.getReadOnlyProperty();
    }
}
