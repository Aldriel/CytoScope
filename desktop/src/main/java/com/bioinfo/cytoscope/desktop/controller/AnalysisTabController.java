package com.bioinfo.cytoscope.desktop.controller;

import com.bioinfo.cytoscope.analysis.ApoptosisAnalysis;
import com.bioinfo.cytoscope.analysis.ApoptosisResult;
import com.bioinfo.cytoscope.analysis.ApoptosisSettings;
import com.bioinfo.cytoscope.analysis.CellCycleAnalysis;
import com.bioinfo.cytoscope.analysis.CellCyclePhase;
import com.bioinfo.cytoscope.analysis.CellCycleResult;
import com.bioinfo.cytoscope.analysis.CellCycleSettings;
import com.bioinfo.cytoscope.analysis.CellFate;
import com.bioinfo.cytoscope.desktop.i18n.I18n;
import com.bioinfo.cytoscope.desktop.i18n.Messages;
import com.bioinfo.cytoscope.desktop.model.DesktopModel;
import com.bioinfo.cytoscope.desktop.model.ExperimentSetup;
import com.bioinfo.cytoscope.desktop.view.plot.DrawnGate;
import com.bioinfo.cytoscope.desktop.view.plot.FlowPlot;
import com.bioinfo.cytoscope.desktop.view.plot.PlotData;
import com.bioinfo.cytoscope.experiment.Experiment;
import com.bioinfo.cytoscope.experiment.Metric;
import com.bioinfo.cytoscope.experiment.SampleInfo;
import com.bioinfo.cytoscope.experiment.SampleRole;
import com.bioinfo.cytoscope.fcs.Channel;
import com.bioinfo.cytoscope.gating.Gate;
import com.bioinfo.cytoscope.gating.GatingResult;
import com.bioinfo.cytoscope.gating.IntervalGate;
import com.bioinfo.cytoscope.gating.Population;
import com.bioinfo.cytoscope.gating.Quadrant;
import com.bioinfo.cytoscope.gating.QuadrantGate;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * Controller of the "Analyses" tab: the annexin V / PI test, whose thresholds are dragged on a
 * preview plot, the cell cycle, and the value compared between the groups.
 */
public final class AnalysisTabController {

    /** Section of the user guide describing this tab. */
    public static final String HELP_SECTION = "analyses";

    /** State of the cells in each quadrant of the annexin V / PI plot. */
    static final Map<Quadrant, CellFate> FATES =
            Map.of(
                    Quadrant.NEGATIVE_NEGATIVE, CellFate.LIVE,
                    Quadrant.POSITIVE_NEGATIVE, CellFate.EARLY_APOPTOTIC,
                    Quadrant.POSITIVE_POSITIVE, CellFate.LATE_APOPTOTIC,
                    Quadrant.NEGATIVE_POSITIVE, CellFate.NECROTIC);

    /** Phases drawn on the DNA histogram; the cells above G2/M are not drawn. */
    private static final int DRAWN_PHASES = 4;

    private final DesktopModel model;
    private final I18n i18n;
    private final Consumer<String> showHelp;
    private String previewId;
    private boolean updating;
    private final Map<String, Metric> metrics = new LinkedHashMap<>();

    @FXML private Node placeholder;
    @FXML private ScrollPane content;
    @FXML private ComboBox<String> previewBox;
    @FXML private CheckBox apoptosisBox;
    @FXML private HBox apoptosisBody;
    @FXML private ComboBox<String> apoptosisParentBox;
    @FXML private ComboBox<String> annexinBox;
    @FXML private ComboBox<String> piBox;
    @FXML private TextField annexinField;
    @FXML private TextField piField;
    @FXML private FlowPlot apoptosisPlot;
    @FXML private CheckBox cellCycleBox;
    @FXML private HBox cellCycleBody;
    @FXML private ComboBox<String> cellCycleParentBox;
    @FXML private ComboBox<String> dnaBox;
    @FXML private Label cellCycleIssue;
    @FXML private FlowPlot cellCyclePlot;
    @FXML private ComboBox<String> metricBox;
    @FXML private ComboBox<String> controlBox;

    /**
     * Creates the controller; the controls are injected afterwards by the FXML loader.
     *
     * @param model application state
     * @param i18n current language
     * @param showHelp opens the user guide at the given section
     */
    public AnalysisTabController(DesktopModel model, I18n i18n, Consumer<String> showHelp) {
        this.model = model;
        this.i18n = i18n;
        this.showHelp = showHelp;
    }

    @FXML
    private void initialize() {
        previewBox.valueProperty().addListener((obs, old, id) -> choose(() -> previewId = id));
        apoptosisBox
                .selectedProperty()
                .addListener(
                        (obs, old, on) ->
                                edit(
                                        e ->
                                                e.withApoptosis(
                                                        on
                                                                ? ExperimentSetup.defaultApoptosis(
                                                                        e, channels())
                                                                : null)));
        cellCycleBox
                .selectedProperty()
                .addListener(
                        (obs, old, on) ->
                                edit(
                                        e ->
                                                e.withCellCycle(
                                                        on
                                                                ? ExperimentSetup.defaultCellCycle(
                                                                        e, channels())
                                                                : null)));
        apoptosisParentBox
                .valueProperty()
                .addListener(
                        (obs, old, id) ->
                                editApoptosis(
                                        a ->
                                                new ApoptosisSettings(
                                                        id,
                                                        a.annexinChannel(),
                                                        a.piChannel(),
                                                        a.annexinThreshold(),
                                                        a.piThreshold())));
        annexinBox
                .valueProperty()
                .addListener(
                        (obs, old, channel) ->
                                editApoptosis(
                                        a ->
                                                new ApoptosisSettings(
                                                        a.parentId(),
                                                        channel,
                                                        a.piChannel(),
                                                        a.annexinThreshold(),
                                                        a.piThreshold())));
        piBox.valueProperty()
                .addListener(
                        (obs, old, channel) ->
                                editApoptosis(
                                        a ->
                                                new ApoptosisSettings(
                                                        a.parentId(),
                                                        a.annexinChannel(),
                                                        channel,
                                                        a.annexinThreshold(),
                                                        a.piThreshold())));
        Controls.onCommit(
                annexinField,
                text -> threshold(text, (a, v) -> a.withThresholds(v, a.piThreshold())));
        Controls.onCommit(
                piField,
                text -> threshold(text, (a, v) -> a.withThresholds(a.annexinThreshold(), v)));
        cellCycleParentBox
                .valueProperty()
                .addListener(
                        (obs, old, id) ->
                                edit(
                                        e ->
                                                e.withCellCycle(
                                                        new CellCycleSettings(
                                                                id,
                                                                e.cellCycle().dnaChannel()))));
        dnaBox.valueProperty()
                .addListener(
                        (obs, old, channel) ->
                                edit(
                                        e ->
                                                e.withCellCycle(
                                                        new CellCycleSettings(
                                                                e.cellCycle().parentId(),
                                                                channel))));
        metricBox
                .valueProperty()
                .addListener(
                        (obs, old, key) ->
                                edit(e -> e.withComparison(metrics.get(key), e.controlGroup())));
        controlBox
                .valueProperty()
                .addListener((obs, old, group) -> edit(e -> e.withComparison(e.metric(), group)));
        apoptosisPlot.setOnMove(
                (id, gate) -> {
                    QuadrantGate cross = (QuadrantGate) gate;
                    editApoptosis(a -> a.withThresholds(cross.xThreshold(), cross.yThreshold()));
                });

        model.experimentProperty().addListener((obs, old, value) -> render());
        model.channelsProperty().addListener((obs, old, value) -> render());
        i18n.messagesProperty().addListener((obs, old, messages) -> render());
        render();
    }

    // ----- Changes -----

    private void choose(Runnable change) {
        if (!updating) {
            change.run();
            render();
        }
    }

    private void edit(UnaryOperator<Experiment> change) {
        if (!updating) {
            model.update(change);
            render();
        }
    }

    private void editApoptosis(UnaryOperator<ApoptosisSettings> change) {
        edit(e -> e.withApoptosis(change.apply(e.apoptosis())));
    }

    private void threshold(String text, ThresholdChange change) {
        ApoptosisSettings settings = model.experimentProperty().get().apoptosis();
        Double value = Texts.parse(text);
        if (settings == null || value == null || value.isNaN()) {
            render();
            return;
        }
        ApoptosisSettings changed = change.apply(settings, value);
        if (!changed.equals(settings)) {
            editApoptosis(a -> change.apply(a, value));
        }
    }

    /** Sets one threshold of the annexin V / PI test. */
    @FunctionalInterface
    private interface ThresholdChange {
        ApoptosisSettings apply(ApoptosisSettings settings, double value);
    }

    @FXML
    private void onSuggest() {
        model.suggestThresholds();
    }

    @FXML
    private void onHelp() {
        showHelp.accept(HELP_SECTION);
    }

    // ----- Rendering -----

    private List<Channel> channels() {
        return model.channelsProperty().get();
    }

    private void render() {
        Experiment experiment = model.experimentProperty().get();
        boolean empty = experiment.samples().isEmpty() || channels().isEmpty();
        show(placeholder, empty);
        show(content, !empty);
        if (empty) {
            apoptosisPlot.setPlot(null);
            cellCyclePlot.setPlot(null);
            return;
        }
        Messages m = i18n.messages();
        if (previewId == null || experiment.sample(previewId).isEmpty()) {
            previewId =
                    experiment.samples().stream()
                            .filter(s -> s.role() == SampleRole.SAMPLE)
                            .findFirst()
                            .orElse(experiment.samples().getFirst())
                            .id();
        }
        Optional<GatingResult> gating = model.gate(previewId);
        updating = true;
        try {
            renderPreviewChoice(experiment);
            renderApoptosis(experiment, gating, m);
            renderCellCycle(experiment, gating, m);
            renderComparison(experiment, m);
        } finally {
            updating = false;
        }
    }

    private void renderPreviewChoice(Experiment experiment) {
        List<String> ids = experiment.samples().stream().map(SampleInfo::id).toList();
        if (!previewBox.getItems().equals(ids)) {
            previewBox.setItems(FXCollections.observableArrayList(ids));
        }
        Controls.labels(
                previewBox, id -> experiment.sample(id).map(SampleInfo::name).orElse(id));
        previewBox.setValue(previewId);
    }

    private void renderApoptosis(
            Experiment experiment, Optional<GatingResult> gating, Messages m) {
        ApoptosisSettings settings = experiment.apoptosis();
        List<String> fluorescence = fluorescence();
        apoptosisBox.setDisable(fluorescence.isEmpty());
        apoptosisBox.setSelected(settings != null);
        show(apoptosisBody, settings != null);
        if (settings == null) {
            apoptosisPlot.setPlot(null);
            return;
        }
        populations(apoptosisParentBox, experiment, settings.parentId());
        channelChoices(annexinBox, fluorescence, settings.annexinChannel());
        channelChoices(piBox, fluorescence, settings.piChannel());
        if (!annexinField.isFocused()) {
            annexinField.setText(Texts.editable(settings.annexinThreshold()));
        }
        if (!piField.isFocused()) {
            piField.setText(Texts.editable(settings.piThreshold()));
        }
        if (gating.isEmpty()) {
            apoptosisPlot.setPlot(null);
            return;
        }
        ApoptosisResult result = ApoptosisAnalysis.analyze(gating.get(), settings);
        apoptosisPlot.setPlot(
                PlotData.of(
                        gating.get(),
                        settings.parentId(),
                        settings.annexinChannel(),
                        settings.piChannel(),
                        null,
                        null,
                        dnaChannel(experiment)));
        List<DrawnGate> gates = new ArrayList<>();
        for (Quadrant quadrant : Quadrant.values()) {
            CellFate fate = FATES.get(quadrant);
            gates.add(
                    new DrawnGate(
                            "fate-" + quadrant.name(),
                            Texts.of(m, "fate", fate),
                            new QuadrantGate(
                                    settings.annexinChannel(),
                                    settings.piChannel(),
                                    settings.annexinThreshold(),
                                    settings.piThreshold(),
                                    quadrant),
                            Texts.percent(m, result.percent(fate)),
                            false));
        }
        apoptosisPlot.setGates(gates);
    }

    private void renderCellCycle(
            Experiment experiment, Optional<GatingResult> gating, Messages m) {
        CellCycleSettings settings = experiment.cellCycle();
        List<String> fluorescence = fluorescence();
        cellCycleBox.setDisable(fluorescence.isEmpty());
        cellCycleBox.setSelected(settings != null);
        show(cellCycleBody, settings != null);
        cellCycleIssue.setText("");
        show(cellCycleIssue, false);
        if (settings == null) {
            cellCyclePlot.setPlot(null);
            return;
        }
        populations(cellCycleParentBox, experiment, settings.parentId());
        channelChoices(dnaBox, fluorescence, settings.dnaChannel());
        if (gating.isEmpty()) {
            cellCyclePlot.setPlot(null);
            return;
        }
        cellCyclePlot.setPlot(
                PlotData.of(
                        gating.get(),
                        settings.parentId(),
                        settings.dnaChannel(),
                        null,
                        null,
                        null,
                        settings.dnaChannel()));
        List<DrawnGate> gates = new ArrayList<>();
        try {
            CellCycleResult result = CellCycleAnalysis.analyze(gating.get(), settings);
            double[] bounds = result.bounds();
            for (int i = 0; i < DRAWN_PHASES; i++) {
                CellCyclePhase phase = CellCyclePhase.values()[i];
                Gate interval =
                        new IntervalGate(
                                settings.dnaChannel(),
                                i == 0 ? Math.min(0, bounds[0]) : bounds[i - 1],
                                bounds[i]);
                gates.add(
                        new DrawnGate(
                                "phase-" + phase.name(),
                                Texts.of(m, "phase", phase),
                                interval,
                                Texts.percent(m, result.percent(phase)),
                                true));
            }
        } catch (IllegalArgumentException e) {
            cellCycleIssue.setText(m.get("issue.NO_G1_PEAK"));
            show(cellCycleIssue, true);
        }
        cellCyclePlot.setGates(gates);
    }

    private void renderComparison(Experiment experiment, Messages m) {
        metrics.clear();
        metrics.put("", null);
        for (Metric metric : ExperimentSetup.metricOptions(experiment)) {
            metrics.put(key(metric), metric);
        }
        List<String> keys = new ArrayList<>(metrics.keySet());
        if (!metricBox.getItems().equals(keys)) {
            metricBox.setItems(FXCollections.observableArrayList(keys));
        }
        Controls.labels(
                metricBox,
                k ->
                        k.isEmpty()
                                ? m.get("analysis.noMetric")
                                : Texts.metric(m, experiment, metrics.get(k)));
        metricBox.setValue(experiment.metric() == null ? "" : key(experiment.metric()));

        List<String> groups = new ArrayList<>(experiment.groups());
        if (!groups.contains(experiment.controlGroup())) {
            groups.addFirst(experiment.controlGroup());
        }
        if (!controlBox.getItems().equals(groups)) {
            controlBox.setItems(FXCollections.observableArrayList(groups));
        }
        Controls.labels(controlBox, g -> g.isEmpty() ? m.get("analysis.noMetric") : g);
        controlBox.setValue(experiment.controlGroup());
    }

    private static String key(Metric metric) {
        return metric.kind().name() + ":" + Objects.toString(metric.target(), "");
    }

    private List<String> fluorescence() {
        return ExperimentSetup.fluorescence(channels()).stream().map(Channel::name).toList();
    }

    private static String dnaChannel(Experiment experiment) {
        return experiment.cellCycle() == null ? null : experiment.cellCycle().dnaChannel();
    }

    private void populations(ComboBox<String> box, Experiment experiment, String selected) {
        Map<String, String> labels = new LinkedHashMap<>();
        indent(experiment.gating().root(), 0, labels);
        List<String> ids = new ArrayList<>(labels.keySet());
        if (!box.getItems().equals(ids)) {
            box.setItems(FXCollections.observableArrayList(ids));
        }
        Controls.labels(box, labels::get);
        box.setValue(selected);
    }

    private static void indent(Population population, int depth, Map<String, String> labels) {
        labels.put(population.id(), "\u00a0\u00a0\u00a0".repeat(depth) + population.name());
        for (Population child : population.children()) {
            indent(child, depth + 1, labels);
        }
    }

    private void channelChoices(ComboBox<String> box, List<String> names, String selected) {
        if (!box.getItems().equals(names)) {
            box.setItems(FXCollections.observableArrayList(names));
        }
        Controls.labels(box, name -> Texts.channel(channels(), name));
        box.setValue(selected);
    }

    private static void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }
}
