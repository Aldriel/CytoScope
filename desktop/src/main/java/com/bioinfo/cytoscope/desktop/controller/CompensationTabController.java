package com.bioinfo.cytoscope.desktop.controller;

import com.bioinfo.cytoscope.compensation.SpilloverMatrix;
import com.bioinfo.cytoscope.desktop.i18n.I18n;
import com.bioinfo.cytoscope.desktop.i18n.Messages;
import com.bioinfo.cytoscope.desktop.model.DesktopModel;
import com.bioinfo.cytoscope.desktop.model.ExperimentSetup;
import com.bioinfo.cytoscope.experiment.CompensationSource;
import com.bioinfo.cytoscope.experiment.Experiment;
import com.bioinfo.cytoscope.experiment.SampleRole;
import com.bioinfo.cytoscope.fcs.Channel;

import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import java.util.List;
import java.util.function.Consumer;

/**
 * Controller of the "Compensation" tab: chooses where the spillover matrix comes from (the files,
 * the single-stain controls or the user) and edits the matrix cell by cell.
 */
public final class CompensationTabController {

    /** Section of the user guide describing this tab. */
    public static final String HELP_SECTION = "compensation";

    /** Spillover above which a cell is highlighted, as a fraction. */
    static final double HIGH_SPILLOVER = 0.05;

    private static final PseudoClass HIGH = PseudoClass.getPseudoClass("high");

    private final DesktopModel model;
    private final I18n i18n;
    private final Consumer<String> showHelp;
    private SpilloverMatrix shownMatrix;
    private boolean updating;

    @FXML private ComboBox<CompensationSource> sourceBox;
    @FXML private Button computeButton;
    @FXML private Label controlsLabel;
    @FXML private Label placeholderLabel;
    @FXML private Label captionLabel;
    @FXML private ScrollPane matrixScroll;
    @FXML private GridPane matrixGrid;

    /**
     * Creates the controller; the controls are injected afterwards by the FXML loader.
     *
     * @param model application state
     * @param i18n current language
     * @param showHelp opens the user guide at the given section
     */
    public CompensationTabController(DesktopModel model, I18n i18n, Consumer<String> showHelp) {
        this.model = model;
        this.i18n = i18n;
        this.showHelp = showHelp;
    }

    @FXML
    private void initialize() {
        Controls.translatedChoices(
                sourceBox,
                CompensationSource.values(),
                CompensationSource.NONE,
                s -> "source." + s.name(),
                i18n);
        sourceBox
                .valueProperty()
                .addListener(
                        (obs, old, source) -> {
                            if (!updating && source != null) {
                                choose(source);
                            }
                        });
        model.experimentProperty().addListener((obs, old, experiment) -> render());
        i18n.messagesProperty().addListener((obs, old, messages) -> render());
        render();
    }

    /** Applies the source chosen in the list, then shows the source actually in use. */
    private void choose(CompensationSource source) {
        switch (source) {
            case NONE -> model.update(e -> e.withCompensation(CompensationSource.NONE, null));
            case FILE -> model.useFileMatrix();
            case CONTROLS -> model.computeMatrix();
            default ->
                    model.update(
                            e ->
                                    e.withCompensation(
                                            CompensationSource.EDITED,
                                            e.matrix() != null
                                                    ? e.matrix()
                                                    : SpilloverMatrix.identity(
                                                            fluorescence())));
        }
        render();
    }

    private List<String> fluorescence() {
        return ExperimentSetup.fluorescence(model.channelsProperty().get()).stream()
                .map(Channel::name)
                .toList();
    }

    @FXML
    private void onCompute() {
        model.computeMatrix();
    }

    @FXML
    private void onHelp() {
        showHelp.accept(HELP_SECTION);
    }

    private void render() {
        Messages m = i18n.messages();
        Experiment experiment = model.experimentProperty().get();
        boolean empty = experiment.samples().isEmpty();
        updating = true;
        sourceBox.setValue(experiment.compensation());
        updating = false;
        sourceBox.setDisable(empty);
        long controls =
                experiment.samples().stream()
                        .filter(s -> s.role() == SampleRole.SINGLE_STAIN)
                        .count();
        computeButton.setDisable(controls == 0);
        controlsLabel.setText(empty ? "" : m.get("compensation.controls", controls));
        SpilloverMatrix matrix = experiment.matrix();
        boolean shown = !empty && matrix != null;
        placeholderLabel.setText(m.get(empty ? "compensation.needsTubes" : "compensation.none"));
        placeholderLabel.setVisible(!shown);
        placeholderLabel.setManaged(!shown);
        captionLabel.setVisible(shown);
        captionLabel.setManaged(shown);
        matrixScroll.setVisible(shown);
        matrixScroll.setManaged(shown);
        if (!shown) {
            shownMatrix = null;
        } else if (!matrix.equals(shownMatrix)) {
            buildMatrix(matrix, m);
        }
    }

    private void buildMatrix(SpilloverMatrix matrix, Messages m) {
        shownMatrix = matrix;
        matrixGrid.getChildren().clear();
        List<Channel> channels = model.channelsProperty().get();
        List<String> names = matrix.channels();
        for (int j = 0; j < names.size(); j++) {
            Label header = new Label(Texts.channel(channels, names.get(j)));
            header.getStyleClass().add("column-header");
            matrixGrid.add(header, j + 1, 0);
        }
        for (int i = 0; i < names.size(); i++) {
            String from = Texts.channel(channels, names.get(i));
            Label header = new Label(from);
            header.getStyleClass().add("column-header");
            matrixGrid.add(header, 0, i + 1);
            for (int j = 0; j < names.size(); j++) {
                if (i == j) {
                    Label diagonal = new Label("100");
                    diagonal.getStyleClass().addAll("numeric", "muted");
                    matrixGrid.add(diagonal, j + 1, i + 1);
                } else {
                    matrixGrid.add(
                            cell(matrix, i, j, from, Texts.channel(channels, names.get(j)), m),
                            j + 1,
                            i + 1);
                }
            }
        }
    }

    private TextField cell(
            SpilloverMatrix matrix, int from, int to, String x, String y, Messages m) {
        double value = matrix.value(from, to);
        TextField field = new TextField(Texts.editable(value * 100));
        field.setPrefColumnCount(5);
        field.getStyleClass().add("numeric");
        field.pseudoClassStateChanged(HIGH, value > HIGH_SPILLOVER);
        field.setAccessibleText(m.get("compensation.cell", x, y));
        Controls.onCommit(
                field,
                text -> {
                    Double percent = Texts.parse(text);
                    if (percent == null || percent.isNaN()) {
                        field.setText(Texts.editable(value * 100));
                    } else if (Math.abs(percent / 100 - value) > 1e-9) {
                        model.update(
                                e ->
                                        e.withCompensation(
                                                CompensationSource.EDITED,
                                                e.matrix().with(from, to, percent / 100)));
                    }
                });
        return field;
    }
}
