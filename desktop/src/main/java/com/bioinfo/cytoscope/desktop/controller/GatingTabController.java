package com.bioinfo.cytoscope.desktop.controller;

import com.bioinfo.cytoscope.desktop.i18n.I18n;
import com.bioinfo.cytoscope.desktop.i18n.Messages;
import com.bioinfo.cytoscope.desktop.model.DesktopModel;
import com.bioinfo.cytoscope.desktop.view.FilePicker;
import com.bioinfo.cytoscope.desktop.view.plot.DrawnGate;
import com.bioinfo.cytoscope.desktop.view.plot.FlowPlot;
import com.bioinfo.cytoscope.desktop.view.plot.PlotData;
import com.bioinfo.cytoscope.desktop.view.plot.PlotTool;
import com.bioinfo.cytoscope.experiment.Experiment;
import com.bioinfo.cytoscope.experiment.SampleInfo;
import com.bioinfo.cytoscope.experiment.SampleRole;
import com.bioinfo.cytoscope.fcs.Channel;
import com.bioinfo.cytoscope.gating.Gate;
import com.bioinfo.cytoscope.gating.GatingResult;
import com.bioinfo.cytoscope.gating.GatingTree;
import com.bioinfo.cytoscope.gating.IntervalGate;
import com.bioinfo.cytoscope.gating.Population;
import com.bioinfo.cytoscope.gating.Quadrant;
import com.bioinfo.cytoscope.gating.QuadrantGate;
import com.bioinfo.cytoscope.scale.ScaleType;

import javafx.collections.FXCollections;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Controller of the "Gating" tab: the tube, population and axes shown, the drawing tool, and the
 * changes of the hierarchy. The gates are shared by every tube.
 */
public final class GatingTabController {

    /** Section of the user guide describing this tab. */
    public static final String HELP_SECTION = "gating";

    private static final Pattern FORWARD = Pattern.compile("^FSC");
    private static final Pattern SIDE = Pattern.compile("^SSC");
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    private final DesktopModel model;
    private final I18n i18n;
    private final Consumer<String> showHelp;
    private final FilePicker filePicker;

    private String sampleId;
    private String populationId = Population.ROOT_ID;
    private String xChannel;
    private String yChannel;
    private final Map<String, ScaleType> scales = new HashMap<>();
    private PlotTool tool = PlotTool.SELECT;
    private String renaming;
    private GatingResult gating;
    private boolean updating;
    private final Map<PlotTool, ToggleButton> toolButtons = new EnumMap<>(PlotTool.class);

    @FXML private HBox placeholder;
    @FXML private HBox content;
    @FXML private VBox populationList;
    @FXML private ComboBox<String> tubeBox;
    @FXML private ComboBox<String> xBox;
    @FXML private ComboBox<ScaleType> xScaleBox;
    @FXML private ComboBox<String> yBox;
    @FXML private ComboBox<ScaleType> yScaleBox;
    @FXML private ToggleButton selectTool;
    @FXML private ToggleButton rectangleTool;
    @FXML private ToggleButton polygonTool;
    @FXML private ToggleButton quadrantTool;
    @FXML private ToggleButton intervalTool;
    @FXML private Label toolHint;
    @FXML private FlowPlot plot;
    @FXML private Label plotCaption;

    /**
     * Creates the controller; the controls are injected afterwards by the FXML loader.
     *
     * @param model application state
     * @param i18n current language
     * @param showHelp opens the user guide at the given section
     * @param filePicker asks where to export a population
     */
    public GatingTabController(
            DesktopModel model, I18n i18n, Consumer<String> showHelp, FilePicker filePicker) {
        this.model = model;
        this.i18n = i18n;
        this.showHelp = showHelp;
        this.filePicker = filePicker;
    }

    @FXML
    private void initialize() {
        for (ComboBox<ScaleType> box : List.of(xScaleBox, yScaleBox)) {
            Controls.translatedChoices(
                    box, ScaleType.values(), ScaleType.LINEAR, s -> "scale." + s.name(), i18n);
        }
        tubeBox.valueProperty().addListener((obs, old, id) -> choose(() -> sampleId = id));
        xBox.valueProperty().addListener((obs, old, x) -> choose(() -> xChannel = x));
        yBox.valueProperty()
                .addListener((obs, old, y) -> choose(() -> yChannel = y.isEmpty() ? null : y));
        xScaleBox
                .valueProperty()
                .addListener((obs, old, s) -> choose(() -> scales.put(xChannel, s)));
        yScaleBox
                .valueProperty()
                .addListener((obs, old, s) -> choose(() -> scales.put(yChannel, s)));

        ToggleGroup tools = new ToggleGroup();
        List<ToggleButton> buttons =
                List.of(selectTool, rectangleTool, polygonTool, quadrantTool, intervalTool);
        for (PlotTool t : PlotTool.values()) {
            ToggleButton button = buttons.get(t.ordinal());
            button.setToggleGroup(tools);
            button.setUserData(t);
            toolButtons.put(t, button);
        }
        tools.selectedToggleProperty()
                .addListener(
                        (obs, old, selected) -> {
                            if (selected == null) {
                                tools.selectToggle(old);
                            } else if (!updating) {
                                setTool((PlotTool) selected.getUserData());
                            }
                        });
        plot.setOnCreate(this::create);
        plot.setOnMove(this::move);
        plot.setOnOpen(this::open);

        model.experimentProperty().addListener((obs, old, value) -> render());
        model.channelsProperty().addListener((obs, old, value) -> render());
        i18n.messagesProperty().addListener((obs, old, messages) -> render());
        render();
    }

    /** Runs a change of the choices made in the lists, unless the lists are being refreshed. */
    private void choose(Runnable change) {
        if (!updating) {
            change.run();
            render();
        }
    }

    // ----- Choices -----

    /**
     * Shows a population, on the axes of its gates if it has any.
     *
     * @param id identifier of the population
     */
    void open(String id) {
        Optional<Population> target = tree().find(id);
        if (target.isPresent()) {
            populationId = id;
            if (target.get().children().stream().anyMatch(c -> c.gate() != null)) {
                setAxes(target.get());
            }
            render();
        }
    }

    private void setTool(PlotTool chosen) {
        tool = chosen;
        render();
    }

    private void setAxes(Population population) {
        List<Channel> channels = model.channelsProperty().get();
        Optional<Gate> gate =
                population.children().stream()
                        .map(Population::gate)
                        .filter(g -> g != null)
                        .findFirst();
        if (gate.isPresent()) {
            xChannel = gate.get().xChannel();
            yChannel = gate.get().yChannel();
            return;
        }
        List<String> names = channels.stream().map(Channel::name).toList();
        xChannel =
                names.stream()
                        .filter(n -> FORWARD.matcher(n).find())
                        .findFirst()
                        .orElse(names.getFirst());
        String x = xChannel;
        yChannel =
                names.stream()
                        .filter(n -> SIDE.matcher(n).find())
                        .findFirst()
                        .or(() -> names.stream().filter(n -> !n.equals(x)).findFirst())
                        .orElse(null);
    }

    // ----- Changes of the hierarchy -----

    private void create(Gate gate) {
        Messages m = i18n.messages();
        String parent = populationId;
        if (gate instanceof QuadrantGate q) {
            List<String> names = new ArrayList<>();
            for (Quadrant quadrant : Quadrant.values()) {
                names.add(
                        Texts.quadrant(
                                channelName(q.xChannel()), channelName(q.yChannel()), quadrant));
            }
            model.update(
                    e ->
                            e.withGating(
                                    e.gating()
                                            .addQuadrants(
                                                    parent,
                                                    names,
                                                    q.xChannel(),
                                                    q.yChannel(),
                                                    q.xThreshold(),
                                                    q.yThreshold())));
        } else {
            int number = tree().root().flatten().size();
            model.update(
                    e ->
                            e.withGating(
                                    e.gating()
                                            .add(
                                                    parent,
                                                    m.get("gating.defaultName", number),
                                                    gate)));
        }
        tool = PlotTool.SELECT;
        render();
    }

    private void move(String id, Gate gate) {
        model.update(e -> e.withGating(e.gating().moveGate(id, gate)));
    }

    private void rename(String id, String name) {
        renaming = null;
        String trimmed = name.strip();
        Optional<Population> population = tree().find(id);
        if (trimmed.isEmpty()
                || population.isEmpty()
                || population.get().name().equals(trimmed)) {
            render();
            return;
        }
        model.update(e -> e.withGating(e.gating().rename(id, trimmed)));
    }

    private void remove(String id) {
        Population removed = tree().find(id).orElseThrow();
        if (removed.find(populationId).isPresent()) {
            populationId =
                    tree().root().parentOf(id).map(Population::id).orElse(Population.ROOT_ID);
        }
        model.update(e -> e.withGating(e.gating().remove(id)));
    }

    private void export(Population population) {
        Messages m = i18n.messages();
        String tube = sampleName(sampleId);
        String name =
                population.id().equals(Population.ROOT_ID)
                        ? tube
                        : tube + " - " + population.name();
        filePicker
                .chooseFcsExport(
                        plot.getScene().getWindow(),
                        m,
                        name.replaceAll("[\\\\/:*?\"<>|]", "_") + ".fcs")
                .ifPresent(file -> model.exportPopulation(sampleId, population.id(), file));
    }

    @FXML
    private void onHelp() {
        showHelp.accept(HELP_SECTION);
    }

    // ----- Rendering -----

    private GatingTree tree() {
        return model.experimentProperty().get().gating();
    }

    private String sampleName(String id) {
        return model.experimentProperty()
                .get()
                .sample(id)
                .map(SampleInfo::name)
                .orElse(id);
    }

    private String channelName(String name) {
        return name.isEmpty()
                ? i18n.messages().get("gating.histogram")
                : Texts.channel(model.channelsProperty().get(), name);
    }

    private void render() {
        Experiment experiment = model.experimentProperty().get();
        boolean empty = experiment.samples().isEmpty() || model.channelsProperty().get().isEmpty();
        show(placeholder, empty);
        show(content, !empty);
        if (empty) {
            gating = null;
            plot.setPlot(null);
            return;
        }
        Messages m = i18n.messages();
        keepChoicesValid(experiment);
        updating = true;
        renderChoices(experiment);
        updating = false;

        Population population = tree().find(populationId).orElseThrow();
        gating = model.gate(sampleId).orElse(null);
        renderHierarchy(m);
        if (gating == null) {
            plot.setPlot(null);
            plotCaption.setText("");
            return;
        }
        String dna = experiment.cellCycle() == null ? null : experiment.cellCycle().dnaChannel();
        PlotData data =
                PlotData.of(
                        gating,
                        populationId,
                        xChannel,
                        yChannel,
                        scales.get(xChannel),
                        yChannel == null ? null : scales.get(yChannel),
                        dna);
        updating = true;
        xScaleBox.setValue(data.xScale().type());
        yScaleBox.setValue(data.yScale() == null ? null : data.yScale().type());
        updating = false;
        yScaleBox.setDisable(yChannel == null);
        plot.setPlot(data);
        plot.setGates(drawnGates(population, m));
        renderTools(m);
        plotCaption.setText(
                population.name() + " · " + m.get("gating.events", data.events()));
    }

    private void keepChoicesValid(Experiment experiment) {
        if (sampleId == null || experiment.sample(sampleId).isEmpty()) {
            sampleId =
                    experiment.samples().stream()
                            .filter(s -> s.role() == SampleRole.SAMPLE)
                            .findFirst()
                            .orElse(experiment.samples().getFirst())
                            .id();
        }
        if (tree().find(populationId).isEmpty()) {
            populationId = Population.ROOT_ID;
        }
        List<String> names = model.channelsProperty().get().stream().map(Channel::name).toList();
        if (!names.contains(xChannel) || (yChannel != null && !names.contains(yChannel))) {
            setAxes(tree().find(populationId).orElseThrow());
        }
    }

    private void renderChoices(Experiment experiment) {
        List<String> ids = experiment.samples().stream().map(SampleInfo::id).toList();
        if (!tubeBox.getItems().equals(ids)) {
            tubeBox.setItems(FXCollections.observableArrayList(ids));
        }
        tubeBox.setValue(sampleId);
        List<String> names = model.channelsProperty().get().stream().map(Channel::name).toList();
        if (!xBox.getItems().equals(names)) {
            xBox.setItems(FXCollections.observableArrayList(names));
            List<String> withHistogram = new ArrayList<>();
            withHistogram.add("");
            withHistogram.addAll(names);
            yBox.setItems(FXCollections.observableArrayList(withHistogram));
        }
        xBox.setValue(xChannel);
        yBox.setValue(yChannel == null ? "" : yChannel);
        Controls.labels(tubeBox, this::sampleName);
        Controls.labels(xBox, this::channelName);
        Controls.labels(yBox, this::channelName);
    }

    private List<DrawnGate> drawnGates(Population population, Messages m) {
        List<DrawnGate> drawn = new ArrayList<>();
        for (Population child : population.children()) {
            Gate gate = child.gate();
            boolean shown =
                    gate instanceof IntervalGate
                            ? yChannel == null && gate.xChannel().equals(xChannel)
                            : yChannel != null
                                    && gate.xChannel().equals(xChannel)
                                    && gate.yChannel().equals(yChannel);
            if (shown) {
                drawn.add(
                        new DrawnGate(
                                child.id(),
                                child.name(),
                                gate,
                                Texts.percent(m, gating.percentOfParent(child.id())),
                                false));
            }
        }
        return drawn;
    }

    private void renderTools(Messages m) {
        boolean histogram = yChannel == null;
        for (Map.Entry<PlotTool, ToggleButton> entry : toolButtons.entrySet()) {
            boolean available = entry.getKey().availableOn(histogram);
            ToggleButton button = entry.getValue();
            button.setDisable(!available);
            button.setTooltip(
                    new Tooltip(
                            available
                                    ? m.get("gating.hint." + entry.getKey().name())
                                    : m.get(
                                            histogram
                                                    ? "gating.needsDotPlot"
                                                    : "gating.needsHistogram")));
        }
        PlotTool shown = tool.availableOn(histogram) ? tool : PlotTool.SELECT;
        updating = true;
        Toggle selected = toolButtons.get(shown);
        selected.setSelected(true);
        updating = false;
        plot.setTool(shown);
        toolHint.setText(m.get("gating.hint." + shown.name()));
    }

    private void renderHierarchy(Messages m) {
        populationList.getChildren().clear();
        addPopulation(tree().root(), 0, m);
    }

    private void addPopulation(Population population, int depth, Messages m) {
        String id = population.id();
        boolean root = id.equals(Population.ROOT_ID);
        boolean selected = id.equals(populationId);
        HBox row = new HBox(6);
        row.getStyleClass().add("population-row");
        row.pseudoClassStateChanged(SELECTED, selected);
        row.setStyle("-fx-padding: 0.15em 0.3em 0.15em " + (0.3 + depth * 1.1) + "em;");
        if (id.equals(renaming)) {
            TextField field = new TextField(population.name());
            field.setAccessibleText(m.get("gating.renamePrompt"));
            HBox.setHgrow(field, Priority.ALWAYS);
            field.setOnKeyPressed(
                    e -> {
                        if (e.getCode() == KeyCode.ESCAPE) {
                            renaming = null;
                            render();
                        }
                    });
            Controls.onCommit(
                    field,
                    text -> {
                        if (id.equals(renaming)) {
                            rename(id, text);
                        }
                    });
            row.getChildren().add(field);
            populationList.getChildren().add(row);
            field.requestFocus();
        } else {
            Button name = new Button(population.name());
            name.getStyleClass().add("population");
            name.setOnAction(e -> open(id));
            Label share = new Label(statistic(id, root, m));
            share.getStyleClass().add("muted");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            row.getChildren().addAll(name, spacer, share);
            populationList.getChildren().add(row);
        }
        if (selected && !id.equals(renaming)) {
            HBox actions = new HBox(4);
            actions.getStyleClass().add("population-actions");
            actions.setStyle("-fx-padding: 0 0 0.2em " + (0.8 + depth * 1.1) + "em;");
            if (!root) {
                actions.getChildren()
                        .addAll(
                                link(
                                        m.get("gating.rename"),
                                        () -> {
                                            renaming = id;
                                            render();
                                        }),
                                link(m.get("gating.remove"), () -> remove(id)));
            }
            Hyperlink export = link(m.get("gating.export"), () -> export(population));
            export.setTooltip(new Tooltip(m.get("tip.export")));
            actions.getChildren().add(export);
            populationList.getChildren().add(actions);
        }
        for (Population child : population.children()) {
            addPopulation(child, depth + 1, m);
        }
    }

    private String statistic(String id, boolean root, Messages m) {
        if (gating == null) {
            return "";
        }
        return root
                ? Texts.count(m, gating.count(id))
                : Texts.percent(m, gating.percentOfParent(id));
    }

    private static Hyperlink link(String text, Runnable action) {
        Hyperlink link = new Hyperlink(text);
        link.setOnAction(e -> action.run());
        return link;
    }

    private static void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }
}
