package com.bioinfo.cytoscope.desktop.controller;

import com.bioinfo.cytoscope.desktop.AppInfo;
import com.bioinfo.cytoscope.desktop.i18n.I18n;
import com.bioinfo.cytoscope.desktop.i18n.Messages;
import com.bioinfo.cytoscope.desktop.model.DesktopModel;
import com.bioinfo.cytoscope.desktop.model.ExperimentSetup;
import com.bioinfo.cytoscope.desktop.view.FilePicker;
import com.bioinfo.cytoscope.experiment.Experiment;
import com.bioinfo.cytoscope.experiment.SampleInfo;
import com.bioinfo.cytoscope.experiment.SampleRole;
import com.bioinfo.cytoscope.fcs.Channel;
import com.bioinfo.cytoscope.service.Example;

import javafx.collections.FXCollections;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * Controller of the "Tubes" tab: opens FCS files (chosen or dropped on the tab) or an example,
 * then edits the role, group and dose of each tube.
 */
public final class TubesTabController {

    /** Section of the user guide describing this tab. */
    public static final String HELP_SECTION = "tubes";

    private static final PseudoClass SIMULATED = PseudoClass.getPseudoClass("simulated");

    private final DesktopModel model;
    private final I18n i18n;
    private final Consumer<String> showHelp;
    private final FilePicker filePicker;
    private final Consumer<String> openUrl;

    /** Rows of the table, by tube identifier, in display order. */
    private final Map<String, Row> rows = new LinkedHashMap<>();

    private List<Channel> shownChannels = List.of();

    @FXML private VBox root;
    @FXML private Button openButton;
    @FXML private HBox banner;
    @FXML private Label bannerLabel;
    @FXML private Hyperlink datasetLink;
    @FXML private Label dropLabel;
    @FXML private Label summaryLabel;
    @FXML private ScrollPane tubeScroll;
    @FXML private GridPane tubeGrid;

    /**
     * Creates the controller; the controls are injected afterwards by the FXML loader.
     *
     * @param model application state
     * @param i18n current language
     * @param showHelp opens the user guide at the given section
     * @param filePicker asks for the files to open
     * @param openUrl opens the page of the example data set
     */
    public TubesTabController(
            DesktopModel model,
            I18n i18n,
            Consumer<String> showHelp,
            FilePicker filePicker,
            Consumer<String> openUrl) {
        this.model = model;
        this.i18n = i18n;
        this.showHelp = showHelp;
        this.filePicker = filePicker;
        this.openUrl = openUrl;
    }

    @FXML
    private void initialize() {
        root.setOnDragOver(this::onDragOver);
        root.setOnDragDropped(this::onDragDropped);
        model.experimentProperty().addListener((obs, old, experiment) -> render());
        model.exampleProperty().addListener((obs, old, example) -> render());
        i18n.messagesProperty()
                .addListener(
                        (obs, old, messages) -> {
                            rows.clear();
                            render();
                        });
        render();
    }

    // ----- Actions -----

    /** Asks for FCS files and adds them to the experiment. */
    public void open() {
        Messages m = i18n.messages();
        List<Path> files = filePicker.chooseFcsFiles(openButton.getScene().getWindow(), m);
        model.openFiles(files, Texts.rootName(m));
    }

    /**
     * Opens an example experiment.
     *
     * @param example the example
     */
    public void openExample(Example example) {
        Messages m = i18n.messages();
        model.openExample(example, Texts.rootName(m), Texts.presetNames(m));
    }

    @FXML
    private void onOpen() {
        open();
    }

    @FXML
    private void onApoptosisExample() {
        openExample(Example.U87_APOPTOSIS);
    }

    @FXML
    private void onCellCycleExample() {
        openExample(Example.U87_CELL_CYCLE);
    }

    @FXML
    private void onCadmiumExample() {
        openExample(Example.CADMIUM);
    }

    @FXML
    private void onOpenDataset() {
        openUrl.accept(AppInfo.DATASET_URL);
    }

    @FXML
    private void onHelp() {
        showHelp.accept(HELP_SECTION);
    }

    private void onDragOver(DragEvent e) {
        if (e.getDragboard().hasFiles()) {
            e.acceptTransferModes(TransferMode.COPY);
        }
        e.consume();
    }

    private void onDragDropped(DragEvent e) {
        List<Path> files = fcsFiles(e.getDragboard().getFiles());
        model.openFiles(files, Texts.rootName(i18n.messages()));
        e.setDropCompleted(!files.isEmpty());
        e.consume();
    }

    /**
     * Keeps the FCS files among files dropped on the tab.
     *
     * @param files the dropped files
     * @return the paths of the {@code .fcs} files
     */
    static List<Path> fcsFiles(List<File> files) {
        return files.stream()
                .filter(f -> f.getName().toLowerCase(Locale.ROOT).endsWith(".fcs"))
                .map(File::toPath)
                .toList();
    }

    // ----- Rendering -----

    private void render() {
        Messages m = i18n.messages();
        Experiment experiment = model.experimentProperty().get();
        Example example = model.exampleProperty().get();
        show(banner, example != null);
        if (example != null) {
            bannerLabel.setText(m.get(example.simulated() ? "tubes.simulated" : "tubes.real"));
            show(datasetLink, !example.simulated());
            banner.pseudoClassStateChanged(SIMULATED, example.simulated());
        }
        boolean empty = experiment.samples().isEmpty();
        show(dropLabel, empty);
        show(summaryLabel, !empty);
        show(tubeScroll, !empty);
        summaryLabel.setText(
                m.get("tubes.summary", experiment.samples().size(), experiment.groups().size()));
        renderRows(experiment, m);
    }

    private void renderRows(Experiment experiment, Messages m) {
        List<String> ids = experiment.samples().stream().map(SampleInfo::id).toList();
        List<Channel> channels = model.channelsProperty().get();
        if (!ids.equals(new ArrayList<>(rows.keySet())) || !channels.equals(shownChannels)) {
            rebuild(experiment, channels, m);
        }
        for (SampleInfo info : experiment.samples()) {
            rows.get(info.id()).update(info);
        }
    }

    private void rebuild(Experiment experiment, List<Channel> channels, Messages m) {
        rows.clear();
        shownChannels = channels;
        tubeGrid.getChildren().clear();
        tubeGrid.addRow(
                0,
                header(m.get("column.tube"), null),
                header(m.get("column.events"), null),
                header(m.get("column.role"), m.get("tip.role")),
                header(m.get("column.group"), m.get("tip.group")),
                header(m.get("column.dose"), m.get("tip.dose")),
                header(m.get("column.stain"), null));
        List<String> stains = new ArrayList<>();
        stains.add("");
        ExperimentSetup.fluorescence(channels).forEach(c -> stains.add(c.name()));
        int index = 1;
        for (SampleInfo info : experiment.samples()) {
            Row row = new Row(info, stains, m);
            rows.put(info.id(), row);
            tubeGrid.addRow(
                    index++, row.name, row.events, row.role, row.group, row.dose, row.stainCell,
                    row.remove);
        }
    }

    private static Label header(String text, String tip) {
        Label label = new Label(text);
        label.getStyleClass().add("column-header");
        if (tip != null) {
            label.setTooltip(new Tooltip(tip));
        }
        return label;
    }

    private static void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    /** Controls of one tube. */
    private final class Row {

        private final String id;
        private final Label name = new Label();
        private final Label events = new Label();
        private final ComboBox<SampleRole> role = new ComboBox<>();
        private final TextField group = new TextField();
        private final TextField dose = new TextField();
        private final ComboBox<String> stain = new ComboBox<>();
        private final Label noStain = new Label(Texts.DASH);
        private final HBox stainCell = new HBox(stain, noStain);
        private final Hyperlink remove = new Hyperlink();
        private SampleInfo shown;
        private boolean updating;

        Row(SampleInfo info, List<String> stains, Messages m) {
            id = info.id();
            String tubeName = info.name();
            events.setText(Texts.count(m, model.eventCount(id)));
            events.getStyleClass().add("numeric");
            role.setItems(FXCollections.observableArrayList(SampleRole.values()));
            Controls.labels(role, r -> Texts.of(m, "role", r));
            role.setAccessibleText(m.get("column.role") + " " + tubeName);
            role.valueProperty()
                    .addListener(
                            (obs, old, value) ->
                                    change(s -> s.withRole(value, s.stainChannel())));
            group.setPrefColumnCount(10);
            group.setAccessibleText(m.get("column.group") + " " + tubeName);
            Controls.onCommit(group, text -> change(s -> s.withGroup(text.strip())));
            dose.setPrefColumnCount(6);
            dose.getStyleClass().add("numeric");
            dose.setAccessibleText(m.get("column.dose") + " " + tubeName);
            Controls.onCommit(dose, this::commitDose);
            stain.setItems(FXCollections.observableArrayList(stains));
            Controls.labels(
                    stain, c -> c.isEmpty() ? Texts.DASH : Texts.channel(shownChannels, c));
            stain.setAccessibleText(m.get("column.stain") + " " + tubeName);
            stain.valueProperty()
                    .addListener(
                            (obs, old, value) ->
                                    change(s -> s.withRole(SampleRole.SINGLE_STAIN, value)));
            noStain.getStyleClass().add("muted");
            remove.setText(m.get("tubes.remove"));
            remove.setAccessibleText(m.get("tubes.removeTube", tubeName));
            remove.setOnAction(e -> model.removeSample(id));
        }

        void update(SampleInfo info) {
            shown = info;
            updating = true;
            name.setText(info.name());
            role.setValue(info.role());
            if (!group.isFocused()) {
                group.setText(info.group());
            }
            if (!dose.isFocused()) {
                dose.setText(Texts.editable(info.dose()));
            }
            boolean single = info.role() == SampleRole.SINGLE_STAIN;
            show(stain, single);
            show(noStain, !single);
            stain.setValue(info.stainChannel());
            updating = false;
        }

        private void commitDose(String text) {
            Double value = Texts.parse(text);
            if (value == null || value < 0) {
                dose.setText(Texts.editable(shown.dose()));
                return;
            }
            change(s -> s.withDose(value));
        }

        private void change(UnaryOperator<SampleInfo> edit) {
            if (updating || shown == null || edit.apply(shown).equals(shown)) {
                return;
            }
            model.update(e -> e.withSampleChanged(id, edit));
        }
    }
}
