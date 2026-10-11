package com.bioinfo.cytoscope.desktop.controller;

import com.bioinfo.cytoscope.analysis.CellCyclePhase;
import com.bioinfo.cytoscope.analysis.CellFate;
import com.bioinfo.cytoscope.desktop.i18n.I18n;
import com.bioinfo.cytoscope.desktop.i18n.Messages;
import com.bioinfo.cytoscope.desktop.model.DesktopModel;
import com.bioinfo.cytoscope.desktop.view.Images;
import com.bioinfo.cytoscope.experiment.DoseResponse;
import com.bioinfo.cytoscope.experiment.Experiment;
import com.bioinfo.cytoscope.experiment.ExperimentResults;
import com.bioinfo.cytoscope.experiment.GroupComparison;
import com.bioinfo.cytoscope.experiment.SampleResult;
import com.bioinfo.cytoscope.experiment.SampleRole;
import com.bioinfo.cytoscope.plot.Charts;
import com.bioinfo.cytoscope.stats.FourParameterLogistic;
import com.bioinfo.cytoscope.stats.OneWayAnova;

import javafx.collections.FXCollections;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Controller of the "Results" tab: the values of each tube, the comparison of the groups (ANOVA
 * and Dunnett's test) and the dose-response curve. The results are computed again when the tab
 * is shown after a change of the set-up.
 */
public final class ResultsTabController {

    /** Section of the user guide describing this tab. */
    public static final String HELP_SECTION = "results";

    /** Phases shown in the table; the cells above G2/M are left out, as in the report. */
    private static final int SHOWN_PHASES = 4;

    private static final PseudoClass STALE = PseudoClass.getPseudoClass("stale");

    private final DesktopModel model;
    private final I18n i18n;
    private final Consumer<String> showHelp;
    private boolean active;

    @FXML private Button refreshButton;
    @FXML private Label stateLabel;
    @FXML private Label placeholderLabel;
    @FXML private ScrollPane content;
    @FXML private TableView<SampleResult> tubeTable;
    @FXML private Label comparisonLabel;
    @FXML private HBox comparisonBox;
    @FXML private TableView<GroupComparison.GroupSummary> groupTable;
    @FXML private ImageView barChart;
    @FXML private Label starsLabel;
    @FXML private Label curveLabel;
    @FXML private HBox curveBox;
    @FXML private TableView<String[]> fitTable;
    @FXML private ImageView doseChart;

    /**
     * Creates the controller; the controls are injected afterwards by the FXML loader.
     *
     * @param model application state
     * @param i18n current language
     * @param showHelp opens the user guide at the given section
     */
    public ResultsTabController(DesktopModel model, I18n i18n, Consumer<String> showHelp) {
        this.model = model;
        this.i18n = i18n;
        this.showHelp = showHelp;
    }

    @FXML
    private void initialize() {
        refreshButton.disableProperty().bind(model.busyProperty());
        model.experimentProperty()
                .addListener(
                        (obs, old, value) -> {
                            refreshIfShown();
                            render();
                        });
        model.resultsProperty().addListener((obs, old, value) -> render());
        model.resultsFreshProperty().addListener((obs, old, value) -> render());
        i18n.messagesProperty().addListener((obs, old, messages) -> render());
        render();
    }

    /**
     * Tells whether the tab is shown; the results are computed again when it becomes visible.
     *
     * @param shown whether the tab is selected
     */
    public void setActive(boolean shown) {
        active = shown;
        refreshIfShown();
    }

    private void refreshIfShown() {
        if (active) {
            model.analyze();
        }
    }

    @FXML
    private void onRefresh() {
        model.analyze();
    }

    @FXML
    private void onHelp() {
        showHelp.accept(HELP_SECTION);
    }

    // ----- Rendering -----

    private void render() {
        Messages m = i18n.messages();
        Experiment experiment = model.experimentProperty().get();
        ExperimentResults results = model.resultsProperty().get();
        boolean empty = experiment.samples().isEmpty();
        refreshButton.setVisible(!empty);
        stateLabel.setText(
                empty || model.resultsFreshProperty().get() ? "" : m.get("results.computing"));
        placeholderLabel.setText(m.get("results.needsTubes"));
        show(placeholderLabel, empty);
        show(content, !empty && results != null);
        if (empty || results == null) {
            return;
        }
        content.pseudoClassStateChanged(STALE, !model.resultsFreshProperty().get());
        renderTubes(experiment, results, m);
        String metric =
                experiment.metric() == null
                        ? ""
                        : Texts.metric(m, experiment, experiment.metric());
        renderComparison(results, metric, m);
        renderCurve(results, metric, m);
    }

    private void renderTubes(Experiment experiment, ExperimentResults results, Messages m) {
        List<TableColumn<SampleResult, String>> columns = new ArrayList<>();
        columns.add(Controls.column(m.get("column.tube"), false, s -> s.info().name()));
        columns.add(
                Controls.column(
                        m.get("column.group"),
                        false,
                        s ->
                                s.info().role() == SampleRole.SAMPLE
                                        ? s.info().group()
                                        : Texts.of(m, "role", s.info().role())));
        columns.add(
                Controls.column(
                        m.get("column.dose"), true, s -> Texts.number(m, s.info().dose(), 2)));
        columns.add(
                Controls.column(
                        m.get("column.events"), true, s -> Texts.count(m, s.eventCount())));
        if (results.samples().stream().anyMatch(s -> s.apoptosis() != null)) {
            for (CellFate fate : CellFate.values()) {
                columns.add(
                        Controls.column(
                                Texts.of(m, "fate", fate),
                                true,
                                s ->
                                        s.apoptosis() == null
                                                ? Texts.DASH
                                                : Texts.percent(m, s.apoptosis().percent(fate))));
            }
        }
        if (experiment.cellCycle() != null) {
            for (int i = 0; i < SHOWN_PHASES; i++) {
                CellCyclePhase phase = CellCyclePhase.values()[i];
                columns.add(
                        Controls.column(
                                Texts.of(m, "phase", phase),
                                true,
                                s ->
                                        s.cellCycle() == null
                                                ? Texts.DASH
                                                : Texts.percent(m, s.cellCycle().percent(phase))));
            }
            columns.add(
                    Controls.column(
                            m.get("results.cv"),
                            true,
                            s ->
                                    s.cellCycle() == null
                                            ? m.get("results.noPeak")
                                            : Texts.percent(m, s.cellCycle().g1Cv())));
        }
        tubeTable.getColumns().setAll(columns);
        tubeTable.setItems(FXCollections.observableArrayList(results.samples()));
    }

    private void renderComparison(ExperimentResults results, String metric, Messages m) {
        GroupComparison comparison = results.comparison();
        show(comparisonBox, comparison != null);
        show(starsLabel, comparison != null);
        if (comparison == null) {
            comparisonLabel.setText(
                    m.get(
                            "results.noComparison",
                            Texts.of(m, "issue", results.comparisonIssue())));
            groupTable.getItems().clear();
            barChart.setImage(null);
            return;
        }
        OneWayAnova anova = comparison.anova();
        comparisonLabel.setText(
                m.get(
                        "results.anova",
                        metric,
                        Texts.number(m, anova.f(), 2),
                        anova.betweenDegrees(),
                        anova.withinDegrees(),
                        pRelation(m, anova.pValue())));
        String control = comparison.controlGroup();
        groupTable
                .getColumns()
                .setAll(
                        List.of(
                                Controls.column(
                                        m.get("column.group"),
                                        false,
                                        g ->
                                                g.name().equals(control)
                                                        ? m.get("results.control", g.name())
                                                        : g.name()),
                                Controls.column(
                                        m.get("column.replicates"),
                                        true,
                                        g -> Integer.toString(g.values().length)),
                                Controls.column(
                                        m.get("column.meanSd"),
                                        true,
                                        g ->
                                                Texts.number(m, g.mean(), 1)
                                                        + " ± "
                                                        + Texts.number(
                                                                m, g.standardDeviation(), 1)),
                                Controls.column(
                                        m.get("column.difference"),
                                        true,
                                        g ->
                                                g.comparison() == null
                                                        ? Texts.DASH
                                                        : difference(m, g)),
                                Controls.column(
                                        m.get("column.pAdjusted"),
                                        true,
                                        g ->
                                                g.comparison() == null
                                                        ? Texts.DASH
                                                        : pValue(m, g.comparison().adjustedP())),
                                Controls.column(
                                        m.get("column.significance"),
                                        false,
                                        GroupComparison.GroupSummary::stars)));
        groupTable.setItems(FXCollections.observableArrayList(comparison.groups()));
        barChart.setImage(Images.toFx(Charts.barChart(metric, comparison, metric)));
        starsLabel.setText(m.get("results.stars", control));
    }

    private void renderCurve(ExperimentResults results, String metric, Messages m) {
        DoseResponse curve = results.doseResponse();
        show(curveBox, curve != null);
        if (curve == null) {
            curveLabel.setText(
                    m.get(
                            "results.noCurve",
                            Texts.of(m, "issue", results.doseResponseIssue())));
            fitTable.getItems().clear();
            doseChart.setImage(null);
            return;
        }
        curveLabel.setText(metric);
        FourParameterLogistic fit = curve.fit();
        String ec50 = Texts.number(m, fit.ec50(), 2);
        if (!Double.isNaN(fit.ec50Low())) {
            ec50 +=
                    " ["
                            + Texts.number(m, fit.ec50Low(), 2)
                            + " – "
                            + Texts.number(m, fit.ec50High(), 2)
                            + "]";
        }
        fitTable.getColumns()
                .setAll(
                        List.of(
                                Controls.column(m.get("column.parameter"), false, r -> r[0]),
                                Controls.column(m.get("column.value"), true, r -> r[1])));
        fitTable.setItems(
                FXCollections.observableArrayList(
                        List.of(
                                new String[] {m.get("results.fit.ec50"), ec50},
                                new String[] {
                                    m.get("results.fit.bottom"), Texts.number(m, fit.bottom(), 1)
                                },
                                new String[] {
                                    m.get("results.fit.top"), Texts.number(m, fit.top(), 1)
                                },
                                new String[] {
                                    m.get("results.fit.hill"), Texts.number(m, fit.hill(), 2)
                                },
                                new String[] {
                                    m.get("results.fit.rSquared"),
                                    Texts.number(m, fit.rSquared(), 3)
                                },
                                new String[] {
                                    m.get("results.fit.points"),
                                    Integer.toString(fit.pointCount())
                                })));
        doseChart.setImage(
                Images.toFx(
                        Charts.doseResponse(metric, curve, m.get("results.doseAxis"), metric)));
    }

    private static String difference(Messages m, GroupComparison.GroupSummary group) {
        return Texts.number(m, group.comparison().difference(), 1);
    }

    /** Formats a p-value: {@code < 0.001} or three decimals. */
    private static String pValue(Messages m, double p) {
        return p < 0.001 ? "< " + Texts.number(m, 0.001, 3) : Texts.number(m, p, 3);
    }

    /** Formats a p-value after its relation sign: {@code < 0.001} or {@code = 0.012}. */
    private static String pRelation(Messages m, double p) {
        String value = pValue(m, p);
        return value.startsWith("<") ? value : "= " + value;
    }

    private static void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }
}
