package com.bioinfo.cytoscope.desktop.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bioinfo.cytoscope.analysis.ApoptosisSettings;
import com.bioinfo.cytoscope.desktop.AppInfo;
import com.bioinfo.cytoscope.desktop.DesktopApp;
import com.bioinfo.cytoscope.desktop.FxThread;
import com.bioinfo.cytoscope.desktop.help.HelpDocument;
import com.bioinfo.cytoscope.desktop.i18n.Messages;
import com.bioinfo.cytoscope.desktop.model.DesktopModel;
import com.bioinfo.cytoscope.desktop.model.UiSettings;
import com.bioinfo.cytoscope.desktop.prefs.UserPreferences;
import com.bioinfo.cytoscope.desktop.view.FilePicker;
import com.bioinfo.cytoscope.desktop.view.plot.FlowPlot;
import com.bioinfo.cytoscope.desktop.view.plot.PlotData;
import com.bioinfo.cytoscope.desktop.view.plot.PlotGeometry;
import com.bioinfo.cytoscope.experiment.CompensationSource;
import com.bioinfo.cytoscope.experiment.Experiment;
import com.bioinfo.cytoscope.experiment.SampleInfo;
import com.bioinfo.cytoscope.experiment.SampleRole;
import com.bioinfo.cytoscope.gating.IntervalGate;
import com.bioinfo.cytoscope.gating.Population;
import com.bioinfo.cytoscope.gating.RectangleGate;
import com.bioinfo.cytoscope.scale.ScaleType;
import com.bioinfo.cytoscope.service.CytoService;

import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.stream.Stream;

/**
 * Functional tests of the main window: the real FXML views, controllers and model, driven like a
 * user would through buttons, menus, fields and the mouse on the plots, on headless JavaFX. Only
 * the file choosers are replaced, by {@link FakeFilePicker}.
 */
class MainWindowTest {

    private static final int TUBES = 0;
    private static final int COMPENSATION = 1;
    private static final int GATING = 2;
    private static final int ANALYSES = 3;
    private static final int RESULTS = 4;
    private static final int REPORT = 5;

    private final Preferences node =
            Preferences.userRoot().node("cytoscope-ui-test-" + UUID.randomUUID());
    private final List<String> openedUrls = new ArrayList<>();
    private final FakeFilePicker files = new FakeFilePicker();
    private final Messages en = Messages.forLocale(Locale.ENGLISH);

    @TempDir Path dir;

    private DesktopModel model;
    private Stage stage;
    private Scene scene;

    /** Answers the file choosers with the paths set by the test. */
    static final class FakeFilePicker implements FilePicker {
        List<Path> fcs = List.of();
        Path export;
        Path report;
        String suggestedName;
        int calls;

        @Override
        public List<Path> chooseFcsFiles(Window owner, Messages m) {
            calls++;
            return fcs;
        }

        @Override
        public Optional<Path> chooseFcsExport(Window owner, Messages m, String suggestedName) {
            calls++;
            this.suggestedName = suggestedName;
            return Optional.ofNullable(export);
        }

        @Override
        public Optional<Path> chooseReport(Window owner, Messages m, String suggestedName) {
            calls++;
            this.suggestedName = suggestedName;
            return Optional.ofNullable(report);
        }
    }

    @BeforeAll
    static void startJavaFx() {
        FxThread.start();
    }

    @BeforeEach
    void openWindow() {
        FxThread.run(
                () -> {
                    UiSettings settings = new UiSettings(new UserPreferences(node));
                    settings.setLocale(Locale.ENGLISH);
                    model =
                            new DesktopModel(
                                    new CytoService(),
                                    Runnable::run,
                                    Runnable::run,
                                    "1.0",
                                    en.get("population.root"));
                    scene =
                            DesktopApp.createScene(
                                    model, settings, openedUrls::add, files, 1280, 860);
                    stage = new Stage();
                    stage.setScene(scene);
                    stage.show();
                });
    }

    @AfterEach
    void closeWindow() throws BackingStoreException {
        FxThread.run(
                () -> {
                    controller().helpWindow().stage().hide();
                    stage.hide();
                });
        node.removeNode();
    }

    // ----- Helpers -----

    private MainController controller() {
        return (MainController) scene.getUserData();
    }

    private TabPane tabs() {
        return (TabPane) scene.lookup("#mainTabs");
    }

    private void select(int tab) {
        FxThread.run(() -> tabs().getSelectionModel().select(tab));
        layout();
    }

    private int selectedTab() {
        return FxThread.call(() -> tabs().getSelectionModel().getSelectedIndex());
    }

    /** Finds a node of a tab; the tab is shown first if its content is not built yet. */
    @SuppressWarnings("unchecked")
    private <T extends Node> T find(int tab, String id) {
        return FxThread.call(
                () -> {
                    Node found = tabs().getTabs().get(tab).getContent().lookup("#" + id);
                    if (found == null) {
                        tabs().getSelectionModel().select(tab);
                        scene.getRoot().applyCss();
                        scene.getRoot().layout();
                        found = tabs().getTabs().get(tab).getContent().lookup("#" + id);
                    }
                    assertNotNull(found, id);
                    return (T) found;
                });
    }

    private String text(int tab, String id) {
        Labeled labeled = find(tab, id);
        return FxThread.call(labeled::getText);
    }

    private boolean shown(Node n) {
        return FxThread.call(() -> n.isVisible() && n.isManaged());
    }

    private void click(int tab, String label) {
        FxThread.run(
                () ->
                        tabs().getTabs().get(tab).getContent().lookupAll(".button").stream()
                                .map(ButtonBase.class::cast)
                                .filter(b -> label.equals(b.getText()))
                                .findFirst()
                                .orElseThrow(() -> new AssertionError("no button " + label))
                                .fire());
    }

    private void menu(String label) {
        FxThread.run(() -> menuItem(label).fire());
    }

    private MenuItem menuItem(String label) {
        MenuBar bar = (MenuBar) scene.lookup("#menuBar");
        return bar.getMenus().stream()
                .flatMap(MainWindowTest::items)
                .filter(item -> label.equals(item.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no menu item " + label));
    }

    private static Stream<MenuItem> items(MenuItem item) {
        return item instanceof Menu menu
                ? Stream.concat(
                        Stream.of(item), menu.getItems().stream().flatMap(MainWindowTest::items))
                : Stream.of(item);
    }

    private String status() {
        return FxThread.call(() -> ((Label) scene.lookup("#statusLabel")).getText());
    }

    private Experiment experiment() {
        return FxThread.call(() -> model.experimentProperty().get());
    }

    private void layout() {
        FxThread.run(
                () -> {
                    scene.getRoot().applyCss();
                    scene.getRoot().layout();
                });
    }

    private void open(String exampleKey) {
        click(TUBES, en.get(exampleKey));
    }

    private void commit(TextField field, String text) {
        FxThread.run(
                () -> {
                    field.setText(text);
                    field.fireEvent(new ActionEvent());
                });
    }

    private <T> void choose(ComboBox<T> box, T value) {
        FxThread.run(() -> box.setValue(value));
    }

    private void mouse(
            FlowPlot plot, EventType<MouseEvent> type, double x, double y, int clicks) {
        FxThread.run(
                () -> {
                    Point2D p = plot.localToScene(x, y);
                    Event.fireEvent(
                            plot,
                            new MouseEvent(
                                    type, p.getX(), p.getY(), p.getX(), p.getY(),
                                    MouseButton.PRIMARY, clicks, false, false, false, false,
                                    true, false, false, false, false, false, null));
                });
    }

    private void clickPlot(FlowPlot plot, double x, double y) {
        mouse(plot, MouseEvent.MOUSE_PRESSED, x, y, 1);
        mouse(plot, MouseEvent.MOUSE_RELEASED, x, y, 1);
    }

    private void dragPlot(FlowPlot plot, double x1, double y1, double x2, double y2) {
        mouse(plot, MouseEvent.MOUSE_PRESSED, x1, y1, 1);
        mouse(plot, MouseEvent.MOUSE_DRAGGED, x2, y2, 1);
        mouse(plot, MouseEvent.MOUSE_RELEASED, x2, y2, 1);
    }

    /** Abscissa, in the plot, of a value of its horizontal axis. */
    private static double plotX(FlowPlot plot, double value) {
        return PlotGeometry.LEFT
                + FxThread.call(() -> plot.plot().xScale().apply(value)) * PlotGeometry.PLOT_SIZE;
    }

    /** Ordinate, in the plot, of a value of its vertical axis. */
    private static double plotY(FlowPlot plot, double value) {
        return PlotGeometry.TOP
                + (1 - FxThread.call(() -> plot.plot().yScale().apply(value)))
                        * PlotGeometry.PLOT_SIZE;
    }

    /** Controls of one row of the tube table, in column order. */
    private List<Node> tubeRow(int row) {
        GridPane grid = find(TUBES, "tubeGrid");
        return FxThread.call(
                () ->
                        grid.getChildren().stream()
                                .filter(n -> Integer.valueOf(row).equals(GridPane.getRowIndex(n)))
                                .toList());
    }

    // ----- Tests -----

    @Test
    void startsTranslatedAndReady() {
        assertEquals(en.get("status.ready"), status());
        assertEquals(
                List.of("Tubes", "Compensation", "Gating", "Analyses", "Results", "Report"),
                FxThread.call(() -> tabs().getTabs().stream().map(t -> t.getText()).toList()));
        assertTrue(FxThread.call(() -> find(REPORT, "saveButton").isDisabled()));
        assertEquals(en.get("report.needsTubes"), text(REPORT, "readinessLabel"));
        assertTrue(shown(find(TUBES, "dropLabel")));
        assertFalse(shown(find(TUBES, "banner")));
        assertEquals(en.get("compensation.needsTubes"), text(COMPENSATION, "placeholderLabel"));
        assertTrue(shown(find(GATING, "placeholder")));
        assertTrue(shown(find(ANALYSES, "placeholder")));
        assertEquals(en.get("results.needsTubes"), text(RESULTS, "placeholderLabel"));
        assertEquals(
                "contact@maximeethier.com",
                FxThread.call(() -> ((Hyperlink) scene.lookup("#emailLink")).getText()));
    }

    @Test
    void factoryRejectsUnknownControllers() {
        ControllerFactory factory =
                new ControllerFactory(
                        model,
                        new UiSettings(new UserPreferences(node)),
                        url -> {},
                        DesktopApp.STYLESHEET,
                        files);
        assertThrows(IllegalArgumentException.class, () -> factory.call(String.class));
    }

    @Nested
    class TubesTab {

        @Test
        void simulatedExampleFillsTheTable() {
            open("example.CADMIUM");
            int tubes = experiment().samples().size();
            assertEquals(24, tubes);
            assertEquals(en.get("status.opened.CADMIUM", tubes), status());
            HBox banner = find(TUBES, "banner");
            assertTrue(shown(banner));
            assertTrue(
                    FxThread.call(
                            () ->
                                    banner.getPseudoClassStates()
                                            .contains(PseudoClass.getPseudoClass("simulated"))));
            assertEquals(en.get("tubes.simulated"), text(TUBES, "bannerLabel"));
            assertFalse(shown(find(TUBES, "datasetLink")));
            assertFalse(shown(find(TUBES, "dropLabel")));
            assertTrue(text(TUBES, "summaryLabel").startsWith("24 tubes"));
            assertEquals(7, tubeRow(1).size());
            assertEquals(
                    experiment().samples().getFirst().name(),
                    FxThread.call(() -> ((Label) tubeRow(1).getFirst()).getText()));
            assertEquals(en.get("report.ready", tubes), text(REPORT, "readinessLabel"));
        }

        @Test
        void realExampleLinksToItsDataset() {
            open("example.U87_APOPTOSIS");
            assertEquals(en.get("tubes.real"), text(TUBES, "bannerLabel"));
            Hyperlink link = find(TUBES, "datasetLink");
            assertTrue(shown(link));
            FxThread.run(link::fire);
            assertEquals(List.of(AppInfo.DATASET_URL), openedUrls);
        }

        @Test
        @SuppressWarnings("unchecked")
        void roleGroupAndDoseAreEdited() {
            open("example.CADMIUM");
            List<Node> row = tubeRow(1);
            String id = experiment().samples().getFirst().id();
            TextField group = (TextField) row.get(3);
            TextField dose = (TextField) row.get(4);

            commit(group, "  Treated ");
            assertEquals("Treated", experiment().sample(id).orElseThrow().group());
            commit(dose, "2,5");
            assertEquals(2.5, experiment().sample(id).orElseThrow().dose());
            commit(dose, "abc");
            assertEquals("2.5", FxThread.call(dose::getText), "invalid doses are reverted");
            commit(dose, "-1");
            assertEquals(2.5, experiment().sample(id).orElseThrow().dose());
            commit(dose, "");
            assertTrue(Double.isNaN(experiment().sample(id).orElseThrow().dose()));

            ComboBox<SampleRole> role = (ComboBox<SampleRole>) row.get(2);
            choose(role, SampleRole.SINGLE_STAIN);
            assertEquals(SampleRole.SINGLE_STAIN, experiment().sample(id).orElseThrow().role());
            ComboBox<String> stain =
                    (ComboBox<String>) ((Parent) tubeRow(1).get(5)).getChildrenUnmodifiable()
                            .getFirst();
            assertTrue(shown(stain));
            choose(stain, "PI-A");
            assertEquals("PI-A", experiment().sample(id).orElseThrow().stainChannel());
            choose(role, SampleRole.SAMPLE);
            assertFalse(shown(stain));
        }

        @Test
        void tubesAreRemoved() {
            open("example.CADMIUM");
            SampleInfo first = experiment().samples().getFirst();
            Hyperlink remove = (Hyperlink) tubeRow(1).get(6);
            FxThread.run(remove::fire);
            assertEquals(23, experiment().samples().size());
            assertEquals(en.get("status.tubeRemoved", first.name()), status());
            FxThread.run(() -> model.removeSample("unknown"));
            assertEquals(23, experiment().samples().size());
        }

        @Test
        void filesAreOpenedThenAdded() throws IOException {
            open("example.CADMIUM");
            Path exported = dir.resolve("cells.fcs");
            files.export = exported;
            FxThread.run(
                    () -> {
                        String id = model.experimentProperty().get().samples().getFirst().id();
                        model.exportPopulation(id, Population.ROOT_ID, exported);
                    });
            assertTrue(Files.size(exported) > 1000);

            files.fcs = List.of();
            click(TUBES, en.get("tubes.open"));
            assertEquals(24, experiment().samples().size(), "cancelled");

            files.fcs = List.of(exported);
            click(TUBES, en.get("tubes.open"));
            assertEquals(en.get("status.filesOpened", 1, 1), status());
            assertNull(FxThread.call(() -> model.exampleProperty().get()), "a new experiment");
            assertFalse(shown(find(TUBES, "banner")));

            select(REPORT);
            menu(en.get("menu.file.open"));
            assertEquals(TUBES, selectedTab());
            assertEquals(2, experiment().samples().size());

            files.fcs = List.of(dir.resolve("missing.fcs"));
            menu(en.get("menu.file.open"));
            assertTrue(status().startsWith("Error: the file could not be read"), status());
            assertTrue(
                    FxThread.call(
                            () ->
                                    scene.lookup("#statusLabel")
                                            .getPseudoClassStates()
                                            .contains(PseudoClass.getPseudoClass("error"))));
        }

        @Test
        void onlyFcsFilesAreDropped() {
            assertEquals(
                    List.of(Path.of("a.fcs"), Path.of("B.FCS")),
                    TubesTabController.fcsFiles(
                            List.of(new File("a.fcs"), new File("notes.txt"), new File("B.FCS"))));
        }
    }

    @Nested
    class CompensationTab {

        private GridPane matrix() {
            return find(COMPENSATION, "matrixGrid");
        }

        private List<TextField> cells() {
            return FxThread.call(
                    () ->
                            matrix().getChildren().stream()
                                    .filter(TextField.class::isInstance)
                                    .map(TextField.class::cast)
                                    .toList());
        }

        @Test
        @SuppressWarnings("unchecked")
        void matrixOfTheFilesIsShownAndEdited() {
            open("example.CADMIUM");
            ComboBox<CompensationSource> source = find(COMPENSATION, "sourceBox");
            assertEquals(CompensationSource.FILE, FxThread.call(source::getValue));
            assertEquals(en.get("compensation.controls", 2), text(COMPENSATION, "controlsLabel"));
            choose(source, CompensationSource.NONE);
            assertEquals(en.get("compensation.none"), text(COMPENSATION, "placeholderLabel"));
            assertFalse(shown(find(COMPENSATION, "matrixScroll")));

            choose(source, CompensationSource.FILE);
            assertEquals(CompensationSource.FILE, experiment().compensation());
            assertTrue(shown(find(COMPENSATION, "matrixScroll")));
            List<TextField> cells = cells();
            assertFalse(cells.isEmpty());

            TextField cell = cells.getFirst();
            commit(cell, "7.5");
            assertEquals(CompensationSource.EDITED, experiment().compensation());
            assertEquals(CompensationSource.EDITED, FxThread.call(source::getValue));
            TextField edited = cells().getFirst();
            assertEquals("7.5", FxThread.call(edited::getText));
            assertTrue(
                    FxThread.call(
                            () ->
                                    edited.getPseudoClassStates()
                                            .contains(PseudoClass.getPseudoClass("high"))));
            Experiment before = experiment();
            commit(edited, "7.5");
            commit(edited, "x");
            assertEquals(before, experiment(), "same or invalid values change nothing");
            assertEquals("7.5", FxThread.call(edited::getText));

            choose(source, CompensationSource.NONE);
            assertNull(experiment().matrix());
            assertFalse(shown(find(COMPENSATION, "matrixScroll")));
        }

        @Test
        @SuppressWarnings("unchecked")
        void matrixFromTheControlsOrByHand() {
            open("example.CADMIUM");
            click(COMPENSATION, en.get("compensation.fromControls"));
            assertTrue(status().startsWith("Compensation matrix computed"), status());
            assertEquals(CompensationSource.CONTROLS, experiment().compensation());

            ComboBox<CompensationSource> source = find(COMPENSATION, "sourceBox");
            choose(source, CompensationSource.NONE);
            choose(source, CompensationSource.EDITED);
            assertEquals(CompensationSource.EDITED, experiment().compensation());
            assertEquals(0.0, experiment().matrix().value(0, 1), "identity");
            choose(source, CompensationSource.CONTROLS);
            assertEquals(CompensationSource.CONTROLS, experiment().compensation());

            menu(en.get("menu.file.new"));
            assertTrue(FxThread.call(() -> find(COMPENSATION, "sourceBox").isDisabled()));
            assertTrue(FxThread.call(() -> find(COMPENSATION, "computeButton").isDisabled()));
        }

        @Test
        @SuppressWarnings("unchecked")
        void filesWithoutMatrix() {
            open("example.U87_CELL_CYCLE");
            ComboBox<CompensationSource> source = find(COMPENSATION, "sourceBox");
            boolean hasMatrix = FxThread.call(() -> model.fileMatrix().isPresent());
            choose(source, CompensationSource.FILE);
            if (hasMatrix) {
                assertEquals(CompensationSource.FILE, experiment().compensation());
            } else {
                assertEquals("Error: " + en.get("error.noFileMatrix"), status());
                assertEquals(CompensationSource.NONE, FxThread.call(source::getValue));
            }
        }
    }

    @Nested
    class GatingTab {

        private FlowPlot plot() {
            return find(GATING, "plot");
        }

        private List<String> populations() {
            VBox list = find(GATING, "populationList");
            return FxThread.call(
                    () ->
                            list.lookupAll(".population").stream()
                                    .map(n -> ((Labeled) n).getText())
                                    .toList());
        }

        private void openPopulation(String name) {
            VBox list = find(GATING, "populationList");
            FxThread.run(
                    () ->
                            list.lookupAll(".population").stream()
                                    .map(ButtonBase.class::cast)
                                    .filter(b -> b.getText().equals(name))
                                    .findFirst()
                                    .orElseThrow()
                                    .fire());
        }

        private void link(String text) {
            VBox list = find(GATING, "populationList");
            FxThread.run(
                    () ->
                            list.lookupAll(".hyperlink").stream()
                                    .map(Hyperlink.class::cast)
                                    .filter(l -> l.getText().equals(text))
                                    .findFirst()
                                    .orElseThrow(() -> new AssertionError("no link " + text))
                                    .fire());
        }

        private void tool(String id) {
            ToggleButton button = find(GATING, id);
            FxThread.run(button::fire);
        }

        private Population child(String name) {
            return experiment().gating().root().flatten().stream()
                    .filter(p -> p.name().equals(name))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no population " + name));
        }

        @Test
        void hierarchyAndPlotOfTheExample() {
            open("example.CADMIUM");
            select(GATING);
            assertEquals(List.of("All events", "Cells"), populations());
            FlowPlot plot = plot();
            PlotData data = FxThread.call(plot::plot);
            assertEquals("FSC-A", data.x().name());
            assertEquals("SSC-A", data.y().name());
            assertEquals(1, FxThread.call(() -> plot.gates().size()));
            assertTrue(text(GATING, "plotCaption").startsWith("All events · "));

            double x = plotX(plot, 145_000);
            double y = plotY(plot, 60_000);
            clickPlot(plot, x, y);
            assertTrue(text(GATING, "plotCaption").startsWith("Cells · "));
            assertTrue(FxThread.call(() -> plot.gates().isEmpty()));

            openPopulation("All events");
            dragPlot(plot, x, y, x + 32, y);
            RectangleGate moved = (RectangleGate) child("Cells").gate();
            assertTrue(moved.xMin() > 30_000, "the gate was dragged to the right");
        }

        @Test
        @SuppressWarnings("unchecked")
        void gatesAreDrawnWithEachTool() {
            open("example.CADMIUM");
            select(GATING);
            FlowPlot plot = plot();
            double l = PlotGeometry.LEFT;
            double t = PlotGeometry.TOP;

            tool("rectangleTool");
            assertEquals(en.get("gating.hint.RECTANGLE"), text(GATING, "toolHint"));
            dragPlot(plot, l + 20, t + 200, l + 100, t + 280);
            assertTrue(populations().contains("Population 2"), populations().toString());
            assertEquals(en.get("gating.hint.SELECT"), text(GATING, "toolHint"));

            tool("polygonTool");
            clickPlot(plot, l + 200, t + 200);
            clickPlot(plot, l + 280, t + 200);
            clickPlot(plot, l + 280, t + 280);
            clickPlot(plot, l + 201, t + 201);
            assertTrue(populations().contains("Population 3"), populations().toString());

            openPopulation("Cells");
            choose(find(GATING, "xBox"), "FITC-A");
            choose(find(GATING, "yBox"), "PI-A");
            choose(find(GATING, "xScaleBox"), ScaleType.LOGICLE);
            choose(find(GATING, "yScaleBox"), ScaleType.ARCSINH);
            assertEquals(ScaleType.LOGICLE, FxThread.call(() -> plot.plot().xScale().type()));
            assertEquals(ScaleType.ARCSINH, FxThread.call(() -> plot.plot().yScale().type()));
            tool("quadrantTool");
            clickPlot(plot, l + 160, t + 160);
            List<Population> quadrants = child("Cells").children();
            assertEquals(4, quadrants.size());
            String quadrant = quadrants.getFirst().name();
            assertTrue(quadrant.contains("FITC") && quadrant.contains("PI"), quadrant);

            choose(find(GATING, "yBox"), "");
            assertTrue(FxThread.call(() -> find(GATING, "rectangleTool").isDisabled()));
            assertTrue(FxThread.call(() -> find(GATING, "yScaleBox").isDisabled()));
            tool("intervalTool");
            dragPlot(plot, l + 100, t + 100, l + 200, t + 100);
            assertTrue(
                    child("Cells").children().stream()
                            .anyMatch(p -> p.gate() instanceof IntervalGate));
            assertEquals(1, FxThread.call(() -> plot.gates().size()), "only the interval");

            choose(find(GATING, "yBox"), "PI-A");
            assertEquals(4, FxThread.call(() -> plot.gates().size()), "only the quadrants");
            tool("intervalTool");
            ToggleButton select = find(GATING, "selectTool");
            assertTrue(FxThread.call(select::isSelected), "unavailable tools fall back");
            FxThread.run(select::fire);
            assertTrue(FxThread.call(select::isSelected), "a tool stays selected");
        }

        @Test
        void populationsAreRenamedDeletedAndExported() throws IOException {
            open("example.CADMIUM");
            select(GATING);
            openPopulation("Cells");
            link("Rename");
            VBox list = find(GATING, "populationList");
            TextField field = FxThread.call(() -> (TextField) list.lookup(".text-field"));
            commit(field, "Live cells");
            assertEquals(List.of("All events", "Live cells"), populations());

            link("Rename");
            TextField again = FxThread.call(() -> (TextField) list.lookup(".text-field"));
            commit(again, "   ");
            assertEquals(List.of("All events", "Live cells"), populations());
            link("Rename");
            TextField escaped = FxThread.call(() -> (TextField) list.lookup(".text-field"));
            FxThread.run(
                    () ->
                            escaped.fireEvent(
                                    new KeyEvent(
                                            KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false,
                                            false, false, false)));
            assertNull(FxThread.call(() -> list.lookup(".text-field")));

            Path file = dir.resolve("export.fcs");
            files.export = file;
            link("Export as FCS");
            assertTrue(files.suggestedName.endsWith(" - Live cells.fcs"), files.suggestedName);
            assertTrue(Files.size(file) > 1000);
            assertTrue(status().endsWith("saved in export.fcs."), status());
            files.export = null;
            link("Export as FCS");

            link("Delete");
            assertEquals(List.of("All events"), populations());
            assertTrue(text(GATING, "plotCaption").startsWith("All events"));
            link("Export as FCS");
            assertFalse(files.suggestedName.contains(" - "), files.suggestedName);
        }

        @Test
        @SuppressWarnings("unchecked")
        void tubeAndChoicesFollowTheExperiment() {
            open("example.U87_CELL_CYCLE");
            select(GATING);
            ComboBox<String> tube = find(GATING, "tubeBox");
            String second = experiment().samples().get(1).id();
            choose(tube, second);
            assertEquals(second, FxThread.call(tube::getValue));
            FlowPlot plot = plot();
            assertEquals("PI-A", FxThread.call(() -> plot.plot().x().name()), "axes of the gate");

            menu("Français");
            assertEquals(
                    Messages.forLocale(Locale.FRENCH).get("gating.hint.SELECT"),
                    text(GATING, "toolHint"));
            menu("English");
            FxThread.run(() -> model.removeSample(second));
            assertFalse(FxThread.call(() -> tube.getItems().contains(second)));
            menu(en.get("menu.file.new"));
            assertTrue(shown(find(GATING, "placeholder")));
            assertNull(FxThread.call(plot::plot));
        }
    }

    @Nested
    class AnalysesTab {

        private ApoptosisSettings apoptosis() {
            return experiment().apoptosis();
        }

        @Test
        @SuppressWarnings("unchecked")
        void annexinThresholdsAreTypedSuggestedOrDragged() {
            open("example.CADMIUM");
            select(ANALYSES);
            CheckBox apoptosisBox = find(ANALYSES, "apoptosisBox");
            assertTrue(FxThread.call(apoptosisBox::isSelected));
            ComboBox<String> annexin = find(ANALYSES, "annexinBox");
            assertEquals("FITC-A", FxThread.call(annexin::getValue));
            FlowPlot plot = find(ANALYSES, "apoptosisPlot");
            assertEquals(4, FxThread.call(() -> plot.gates().size()));

            commit(find(ANALYSES, "annexinField"), "5000");
            assertEquals(5000, apoptosis().annexinThreshold());
            commit(find(ANALYSES, "piField"), "3000");
            assertEquals(3000, apoptosis().piThreshold());
            ApoptosisSettings typed = apoptosis();
            commit(find(ANALYSES, "annexinField"), "abc");
            commit(find(ANALYSES, "piField"), "3000");
            assertEquals(typed, apoptosis());
            assertEquals(
                    "5000",
                    FxThread.call(() -> ((TextField) find(ANALYSES, "annexinField")).getText()));

            click(ANALYSES, en.get("analysis.suggest"));
            assertEquals(en.get("status.thresholdsSuggested"), status());

            layout();
            ApoptosisSettings before = apoptosis();
            double x = plotX(plot, before.annexinThreshold());
            double y = plotY(plot, before.piThreshold());
            dragPlot(plot, x - 20, y + 20, x + 12, y + 20);
            assertTrue(apoptosis().annexinThreshold() > before.annexinThreshold());
            assertEquals(before.piThreshold(), apoptosis().piThreshold(), 1e-3);

            choose(find(ANALYSES, "piBox"), "FITC-A");
            assertTrue(status().startsWith("Error: invalid input"), status());
            assertEquals("PI-A", apoptosis().piChannel());
        }

        @Test
        @SuppressWarnings("unchecked")
        void analysesAreSwitchedAndSetUp() {
            open("example.CADMIUM");
            select(ANALYSES);
            CheckBox apoptosisBox = find(ANALYSES, "apoptosisBox");
            FxThread.run(apoptosisBox::fire);
            assertNull(apoptosis());
            assertFalse(shown(find(ANALYSES, "apoptosisBody")));
            FxThread.run(apoptosisBox::fire);
            assertNotNull(apoptosis());

            ComboBox<String> parent = find(ANALYSES, "apoptosisParentBox");
            choose(parent, Population.ROOT_ID);
            assertEquals(Population.ROOT_ID, apoptosis().parentId());

            CheckBox cellCycleBox = find(ANALYSES, "cellCycleBox");
            FxThread.run(cellCycleBox::fire);
            assertNotNull(experiment().cellCycle());
            choose(find(ANALYSES, "dnaBox"), "PI-A");
            choose(find(ANALYSES, "cellCycleParentBox"), Population.ROOT_ID);
            assertEquals(Population.ROOT_ID, experiment().cellCycle().parentId());
            FlowPlot cycle = find(ANALYSES, "cellCyclePlot");
            boolean issue = shown(find(ANALYSES, "cellCycleIssue"));
            assertTrue(issue || FxThread.call(() -> cycle.gates().size()) == 4);

            ComboBox<String> metric = find(ANALYSES, "metricBox");
            choose(metric, "");
            assertNull(experiment().metric());
            String phase =
                    FxThread.call(
                            () ->
                                    metric.getItems().stream()
                                            .filter(k -> k.startsWith("CELL_CYCLE"))
                                            .findFirst()
                                            .orElseThrow());
            choose(metric, phase);
            assertNotNull(experiment().metric());
            ComboBox<String> control = find(ANALYSES, "controlBox");
            String group = FxThread.call(() -> control.getItems().getLast());
            choose(control, group);
            assertEquals(group, experiment().controlGroup());

            ComboBox<String> preview = find(ANALYSES, "previewBox");
            String last = experiment().samples().getLast().id();
            choose(preview, last);
            assertEquals(last, FxThread.call(preview::getValue));
            FxThread.run(cellCycleBox::fire);
            assertNull(experiment().cellCycle());
        }

        @Test
        void cellCycleOfTheRealExample() {
            open("example.U87_CELL_CYCLE");
            select(ANALYSES);
            FlowPlot cycle = find(ANALYSES, "cellCyclePlot");
            assertEquals(4, FxThread.call(() -> cycle.gates().size()));
            assertTrue(FxThread.call(() -> cycle.gates().stream().allMatch(g -> g.locked())));
            assertNull(FxThread.call(() -> ((FlowPlot) find(ANALYSES, "apoptosisPlot")).plot()));
        }
    }

    @Nested
    class ResultsTab {

        @Test
        void resultsOfTheDoseSeries() {
            open("example.CADMIUM");
            select(RESULTS);
            assertEquals(en.get("status.analyzed"), status());
            TableView<?> tubes = find(RESULTS, "tubeTable");
            assertEquals(24, FxThread.call(() -> tubes.getItems().size()));
            assertEquals(
                    List.of("Tube", "Group", "Dose", "Events"),
                    FxThread.call(
                            () ->
                                    tubes.getColumns().stream()
                                            .limit(4)
                                            .map(c -> c.getText())
                                            .toList()));
            assertEquals(8, FxThread.call(() -> tubes.getColumns().size()), "and four fates");
            assertTrue(text(RESULTS, "comparisonLabel").contains("one-way ANOVA"));
            TableView<?> groups = find(RESULTS, "groupTable");
            assertTrue(FxThread.call(() -> groups.getItems().size()) >= 2);
            assertNotNull(FxThread.call(() -> ((ImageView) find(RESULTS, "barChart")).getImage()));
            TableView<?> fit = find(RESULTS, "fitTable");
            assertEquals(6, FxThread.call(() -> fit.getItems().size()));
            assertNotNull(
                    FxThread.call(() -> ((ImageView) find(RESULTS, "doseChart")).getImage()));
            layout();
            assertTrue(
                    FxThread.call(
                            () ->
                                    groups.lookupAll(".table-cell").stream()
                                            .map(c -> ((Labeled) c).getText())
                                            .anyMatch(s -> s != null && s.endsWith("(control)"))));

            ComboBox<String> metric = find(ANALYSES, "metricBox");
            choose(metric, "");
            select(RESULTS);
            assertTrue(text(RESULTS, "comparisonLabel").startsWith("The groups could not"));
            assertTrue(text(RESULTS, "curveLabel").startsWith("No dose-response curve"));
            assertFalse(shown(find(RESULTS, "comparisonBox")));
        }

        @Test
        void staleResultsAreComputedAgain() {
            open("example.U87_CELL_CYCLE");
            select(RESULTS);
            TableView<?> tubes = find(RESULTS, "tubeTable");
            assertTrue(
                    FxThread.call(
                            () ->
                                    tubes.getColumns().stream()
                                            .anyMatch(c -> c.getText().equals("G0/G1 CV"))));
            select(TUBES);
            FxThread.run(
                    () ->
                            model.update(
                                    e ->
                                            e.withComparison(
                                                    e.metric(),
                                                    new ArrayList<>(e.groups()).getLast())));
            assertEquals(en.get("results.computing"), text(RESULTS, "stateLabel"));
            select(TUBES);
            click(RESULTS, en.get("results.refresh"));
            assertEquals("", text(RESULTS, "stateLabel"));
            FxThread.run(model::analyze);
            assertEquals(en.get("status.analyzed"), status());
        }
    }

    @Nested
    class ReportTab {

        @Test
        @SuppressWarnings("unchecked")
        void savesTheReportOnA4InFrenchAndOpensIt() throws IOException {
            open("example.CADMIUM");
            Path file = dir.resolve("report.pdf");
            files.report = file;
            FxThread.run(
                    () -> {
                        MainWindowTest.this
                                .<ComboBox<Locale>>find(REPORT, "languageBox")
                                .setValue(Locale.FRENCH);
                        MainWindowTest.this
                                .<ComboBox<ReportTabController.Paper>>find(REPORT, "paperBox")
                                .setValue(ReportTabController.Paper.A4);
                        MainWindowTest.this
                                .<TextInputControl>find(REPORT, "analystField")
                                .setText("M. Ethier");
                        MainWindowTest.this
                                .<TextInputControl>find(REPORT, "titleField")
                                .setText("Cd 24 h: a/b");
                    });
            click(REPORT, en.get("report.save"));

            assertEquals("Cd 24 h_ a_b.pdf", files.suggestedName);
            assertTrue(Files.size(file) > 1000);
            assertEquals(List.of(file.toUri().toString()), openedUrls);
            assertEquals(en.get("status.reportSaved", "report.pdf"), status());
            assertEquals(595, pageWidth(file), 1, "A4");
        }

        @Test
        @SuppressWarnings("unchecked")
        void letterPaperFromTheMenu() throws IOException {
            open("example.U87_APOPTOSIS");
            Path file = dir.resolve("letter.pdf");
            files.report = file;
            FxThread.run(
                    () ->
                            MainWindowTest.this
                                    .<ComboBox<ReportTabController.Paper>>find(REPORT, "paperBox")
                                    .setValue(ReportTabController.Paper.LETTER));
            menu(en.get("menu.file.report"));
            assertEquals(REPORT, selectedTab());
            assertEquals(en.get("report.title.prompt") + ".pdf", files.suggestedName);
            assertEquals(612, pageWidth(file), 1, "US Letter");
        }

        @Test
        void cancelledChooserAndNothingToSave() {
            menu(en.get("menu.file.report"));
            assertEquals(0, files.calls, "no tubes");
            FxThread.run(
                    () ->
                            model.writeReport(
                                    dir.resolve("none.pdf"), null, saved -> {}));
            assertEquals("Error: " + en.get("error.noTubes"), status());

            open("example.CADMIUM");
            files.report = null;
            menu(en.get("menu.file.report"));
            assertEquals(1, files.calls);
            assertTrue(openedUrls.isEmpty());
        }

        @Test
        void paperFollowsTheCountry() {
            assertEquals(
                    ReportTabController.Paper.LETTER,
                    ReportTabController.Paper.of(Locale.CANADA_FRENCH));
            assertEquals(ReportTabController.Paper.LETTER, ReportTabController.Paper.of(Locale.US));
            assertEquals(ReportTabController.Paper.A4, ReportTabController.Paper.of(Locale.FRANCE));
            assertEquals(
                    ReportTabController.Paper.A4, ReportTabController.Paper.of(Locale.ENGLISH));
        }

        private static float pageWidth(Path file) throws IOException {
            try (PDDocument pdf = Loader.loadPDF(file.toFile())) {
                return pdf.getPage(0).getMediaBox().getWidth();
            }
        }
    }

    @Nested
    class MenusAndShortcuts {

        @Test
        void languageChangesEveryText() {
            open("example.CADMIUM");
            menu("Français");
            assertEquals("Fenêtrage", FxThread.call(() -> tabs().getTabs().get(GATING).getText()));
            assertTrue(status().startsWith("Exemple"), status());
            assertTrue(text(TUBES, "summaryLabel").startsWith("24 tubes"));
            assertTrue(text(REPORT, "readinessLabel").startsWith("Rapport"));
            menu("English");
            assertEquals("Gating", FxThread.call(() -> tabs().getTabs().get(GATING).getText()));
        }

        @Test
        void textSizeMenuAndShortcuts() {
            Parent root = scene.getRoot();
            menu("Large");
            String large = FxThread.call(root::getStyle);
            menu("Larger text");
            assertFalse(large.equals(FxThread.call(root::getStyle)));
            menu("Default text size");
            String normal = FxThread.call(root::getStyle);
            shortcut(KeyCode.ADD);
            shortcut(KeyCode.SUBTRACT);
            assertEquals(normal, FxThread.call(root::getStyle));
            shortcut(KeyCode.PLUS);
            shortcut(KeyCode.NUMPAD0);
            menu("Smaller text");
            assertFalse(normal.equals(FxThread.call(root::getStyle)));
        }

        @Test
        void ctrlDigitsSelectTheTabs() {
            shortcut(KeyCode.DIGIT3);
            assertEquals(GATING, selectedTab());
            for (KeyCode key :
                    List.of(KeyCode.DIGIT6, KeyCode.DIGIT5, KeyCode.DIGIT4, KeyCode.DIGIT2)) {
                shortcut(key);
            }
            assertEquals(COMPENSATION, selectedTab());
            shortcut(KeyCode.DIGIT1);
            assertEquals(TUBES, selectedTab());
        }

        @Test
        void examplesFromTheMenuAndNewExperiment() {
            menu(en.get("example.U87_CELL_CYCLE"));
            assertEquals(10, experiment().samples().size());
            menu(en.get("example.U87_APOPTOSIS"));
            assertNotNull(experiment().apoptosis());
            select(REPORT);
            menu(en.get("example.CADMIUM"));
            assertEquals(TUBES, selectedTab());
            select(REPORT);
            menu(en.get("menu.file.new"));
            assertEquals(TUBES, selectedTab());
            assertTrue(experiment().samples().isEmpty());
            assertEquals(en.get("status.ready"), status());
        }

        @Test
        void helpOpensTheSectionOfTheTab() {
            for (int tab = TUBES; tab <= REPORT; tab++) {
                click(tab, "?");
            }
            assertTrue(FxThread.call(() -> controller().helpWindow().stage().isShowing()));
            select(GATING);
            menu(en.get("menu.help.guide"));
            assertEquals(
                    "gating",
                    FxThread.call(
                            () -> {
                                @SuppressWarnings("unchecked")
                                ListView<HelpDocument.SearchHit> sections =
                                        (ListView<HelpDocument.SearchHit>)
                                                controller()
                                                        .helpWindow()
                                                        .stage()
                                                        .getScene()
                                                        .lookup("#helpSectionList");
                                return sections.getSelectionModel()
                                        .getSelectedItem()
                                        .section()
                                        .id();
                            }));
        }

        @Test
        void linksOpenTheWebsites() {
            menu(en.get("menu.help.fcsStandard"));
            menu(en.get("menu.help.dataset"));
            FxThread.run(
                    () -> {
                        scene.getRoot()
                                .lookupAll(".hyperlink")
                                .stream()
                                .filter(Node::isVisible)
                                .forEach(l -> ((Hyperlink) l).fire());
                        ((ButtonBase) scene.lookup("#kofiButton")).fire();
                    });
            assertTrue(openedUrls.contains(AppInfo.FCS_STANDARD_URL), openedUrls.toString());
            assertTrue(openedUrls.contains(AppInfo.DATASET_URL));
            assertTrue(openedUrls.contains("mailto:contact@maximeethier.com"));
        }

        private void shortcut(KeyCode key) {
            FxThread.run(
                    () ->
                            scene.getAccelerators()
                                    .get(new KeyCodeCombination(key, KeyCombination.SHORTCUT_DOWN))
                                    .run());
        }
    }
}
