package com.bioinfo.cytoscope.desktop.tools;

import com.bioinfo.cytoscope.desktop.AppInfo;
import com.bioinfo.cytoscope.desktop.DesktopApp;
import com.bioinfo.cytoscope.desktop.i18n.Messages;
import com.bioinfo.cytoscope.desktop.model.DesktopModel;
import com.bioinfo.cytoscope.desktop.model.UiSettings;
import com.bioinfo.cytoscope.desktop.prefs.UserPreferences;
import com.bioinfo.cytoscope.desktop.view.FilePicker;
import com.bioinfo.cytoscope.service.CytoService;
import com.bioinfo.cytoscope.service.Example;
import com.bioinfo.cytoscope.service.ExampleLibrary;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextInputControl;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.transform.Transform;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.imageio.ImageIO;

/**
 * Regenerates the annotated screenshots of the user guide, in English and French, by driving the
 * real view with the demonstration data. Images are written to {@code help/images/<lang>} in the
 * resources of the desktop module. Run with {@code mvnw -pl desktop javafx:run@screenshots}.
 */
public final class ScreenshotGenerator extends Application {

    private static final double SCALE = 1.5;
    private static final Color CALLOUT = new Color(0xE5, 0x39, 0x35);
    private static final List<Locale> LOCALES = List.of(Locale.FRENCH, Locale.ENGLISH);

    /** Never asks for a file: the screenshots use the demonstration data. */
    private static final FilePicker NO_FILES =
            new FilePicker() {
                @Override
                public List<Path> chooseFcsFiles(Window owner, Messages m) {
                    return List.of();
                }

                @Override
                public Optional<Path> chooseFcsExport(Window owner, Messages m, String name) {
                    return Optional.empty();
                }

                @Override
                public Optional<Path> chooseReport(Window owner, Messages m, String name) {
                    return Optional.empty();
                }
            };

    private static final int TUBES = 0;
    private static final int COMPENSATION = 1;
    private static final int GATING = 2;
    private static final int ANALYSES = 3;
    private static final int RESULTS = 4;
    private static final int REPORT = 5;

    /**
     * A numbered box drawn around the union of the nodes matching the selectors ({@code #id} or
     * {@code .class}).
     */
    private record Callout(int number, String... selectors) {}

    /** A screenshot: the tab shown, what to do before taking it, and the parts to point out. */
    private record Step(String file, int tab, Runnable action, List<Callout> callouts) {}

    private Stage stage;
    private Scene scene;
    private DesktopModel model;
    private UiSettings settings;
    private Preferences preferencesNode;
    private Path imagesRoot;
    private Path outputDir;

    /**
     * Starts the generator.
     *
     * @param args unused
     */
    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");
        launch(args);
    }

    @Override
    public void start(Stage stage) throws IOException {
        System.setProperty("java.awt.headless", "true");
        this.stage = stage;
        Path cwd = Path.of("").toAbsolutePath();
        Path projectRoot = Files.isDirectory(cwd.resolve("desktop")) ? cwd : cwd.getParent();
        imagesRoot =
                projectRoot
                        .resolve("desktop/src/main/resources")
                        .resolve("com/bioinfo/cytoscope/desktop/help/images");
        preferencesNode = Preferences.userRoot().node("com/bioinfo/cytoscope/desktop/screenshots");
        runLocale(0);
    }

    private void runLocale(int index) throws IOException {
        if (index >= LOCALES.size()) {
            try {
                preferencesNode.removeNode();
            } catch (BackingStoreException e) {
                System.err.println("Could not remove temporary preferences: " + e);
            }
            System.out.println("Screenshots written to " + imagesRoot);
            Platform.exit();
            return;
        }
        Locale locale = LOCALES.get(index);
        outputDir = imagesRoot.resolve(locale.getLanguage());
        Files.createDirectories(outputDir);

        // Synchronous executors: every step finishes before its screenshot is taken.
        settings = new UiSettings(new UserPreferences(preferencesNode));
        settings.setLocale(locale);
        settings.resetFontSize();
        model =
                new DesktopModel(
                        new CytoService(),
                        Runnable::run,
                        Runnable::run,
                        AppInfo.version(),
                        settings.i18n().messages().get("population.root"));
        scene = DesktopApp.createScene(model, settings, url -> {}, NO_FILES, 1280, 820);
        stage.setTitle(DesktopApp.TITLE);
        stage.setScene(scene);
        stage.show();
        System.out.println(locale.getDisplayLanguage(Locale.ENGLISH) + ":");
        run(
                steps(),
                0,
                () -> {
                    try {
                        runLocale(index + 1);
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                });
    }

    private List<Step> steps() {
        return List.of(
                new Step(
                        "01-tubes.png",
                        TUBES,
                        () -> open(Example.CADMIUM),
                        List.of(
                                new Callout(1, "#openButton", "#examplePane"),
                                new Callout(2, "#banner"),
                                new Callout(3, "#tubeGrid"))),
                new Step(
                        "02-compensation.png",
                        COMPENSATION,
                        () -> {},
                        List.of(
                                new Callout(1, "#sourceBox", "#computeButton"),
                                new Callout(2, "#matrixGrid"))),
                new Step(
                        "03-gating.png",
                        GATING,
                        () -> {},
                        List.of(
                                new Callout(1, "#populationList"),
                                new Callout(2, "#xBox", "#yScaleBox"),
                                new Callout(3, "#selectTool", "#intervalTool"),
                                new Callout(4, "#plot"))),
                new Step(
                        "04-apoptosis.png",
                        ANALYSES,
                        () -> scroll(0),
                        List.of(
                                new Callout(1, "#annexinBox", "#piField"),
                                new Callout(2, "#apoptosisPlot"))),
                new Step(
                        "05-results.png",
                        RESULTS,
                        () -> scroll(0.45),
                        List.of(new Callout(1, "#groupTable"), new Callout(2, "#barChart"))),
                new Step(
                        "06-dose-response.png",
                        RESULTS,
                        () -> scroll(1),
                        List.of(new Callout(1, "#fitTable"), new Callout(2, "#doseChart"))),
                new Step(
                        "07-report.png",
                        REPORT,
                        () -> {
                            this.<TextInputControl>node("analystField").setText("M. Ethier");
                            this.<TextInputControl>node("notesArea")
                                    .setText("Simulated series, 24 h exposure");
                        },
                        List.of(
                                new Callout(1, "#titleField", "#paperBox"),
                                new Callout(2, "#analystField", "#notesArea"),
                                new Callout(3, "#saveButton"))),
                new Step(
                        "08-cell-cycle.png",
                        ANALYSES,
                        () -> {
                            open(Example.U87_CELL_CYCLE);
                            scroll(0.5);
                        },
                        List.of(
                                new Callout(1, "#dnaBox"),
                                new Callout(2, "#cellCyclePlot"))));
    }

    private void open(Example example) {
        Messages m = settings.i18n().messages();
        model.openExample(
                example,
                m.get("population.root"),
                settings.i18n().locale().getLanguage().equals("fr")
                        ? ExampleLibrary.PresetNames.FRENCH
                        : ExampleLibrary.PresetNames.ENGLISH);
    }

    /** Scrolls the visible scroll pane of the current tab to a share of its height. */
    private void scroll(double share) {
        scene.getRoot().lookupAll("#content").stream()
                .filter(ScreenshotGenerator::treeVisible)
                .filter(ScrollPane.class::isInstance)
                .map(ScrollPane.class::cast)
                .forEach(pane -> pane.setVvalue(share));
    }

    private void run(List<Step> steps, int index, Runnable done) {
        if (index >= steps.size()) {
            done.run();
            return;
        }
        Step step = steps.get(index);
        this.<TabPane>node("mainTabs").getSelectionModel().select(step.tab());
        try {
            step.action().run();
        } catch (RuntimeException e) {
            System.err.println(step.file() + ": " + e);
        }
        PauseTransition pause = new PauseTransition(Duration.millis(800));
        pause.setOnFinished(
                e -> {
                    try {
                        capture(step);
                    } catch (IOException ex) {
                        System.err.println(step.file() + ": " + ex);
                    }
                    run(steps, index + 1, done);
                });
        pause.play();
    }

    private void capture(Step step) throws IOException {
        SnapshotParameters params = new SnapshotParameters();
        params.setTransform(Transform.scale(SCALE, SCALE));
        WritableImage image = scene.getRoot().snapshot(params, null);
        BufferedImage buffered = toBufferedImage(image);

        Graphics2D g = buffered.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, (int) (13 * SCALE)));
        for (Callout callout : step.callouts()) {
            Bounds bounds = unionBounds(callout.selectors());
            if (bounds != null) {
                drawCallout(g, callout.number(), bounds, buffered.getWidth(), buffered.getHeight());
            } else {
                System.err.println(
                        "  " + step.file() + ": callout " + callout.number() + " not found");
            }
        }
        g.dispose();

        Path file = outputDir.resolve(step.file());
        ImageIO.write(buffered, "png", file.toFile());
        System.out.println(
                "  "
                        + file.getFileName()
                        + "  —  "
                        + model.statusProperty().get().render(settings.i18n().messages()));
    }

    /**
     * Returns the bounds enclosing the first visible node matching each selector, or {@code null}
     * if none.
     */
    private Bounds unionBounds(String... selectors) {
        List<Bounds> found = new ArrayList<>();
        for (String selector : selectors) {
            scene.getRoot().lookupAll(selector).stream()
                    .filter(ScreenshotGenerator::treeVisible)
                    .map(n -> n.localToScene(n.getBoundsInLocal()))
                    .filter(b -> b.getWidth() > 0 && b.getHeight() > 0)
                    .findFirst()
                    .ifPresent(found::add);
        }
        if (found.isEmpty()) {
            return null;
        }
        double minX = found.stream().mapToDouble(Bounds::getMinX).min().orElseThrow();
        double minY = found.stream().mapToDouble(Bounds::getMinY).min().orElseThrow();
        double maxX = found.stream().mapToDouble(Bounds::getMaxX).max().orElseThrow();
        double maxY = found.stream().mapToDouble(Bounds::getMaxY).max().orElseThrow();
        return new BoundingBox(minX, minY, maxX - minX, maxY - minY);
    }

    private static boolean treeVisible(Node node) {
        for (Node n = node; n != null; n = n.getParent()) {
            if (!n.isVisible()) {
                return false;
            }
        }
        return node.getScene() != null;
    }

    private static void drawCallout(Graphics2D g, int number, Bounds b, int width, int height) {
        int pad = (int) (3 * SCALE);
        int x = (int) Math.max(1, b.getMinX() * SCALE - pad);
        int y = (int) Math.max(1, b.getMinY() * SCALE - pad);
        int w = (int) Math.min(width - x - 2, b.getWidth() * SCALE + 2 * pad);
        int h = (int) Math.min(height - y - 2, b.getHeight() * SCALE + 2 * pad);
        g.setColor(CALLOUT);
        g.setStroke(new BasicStroke((float) (2.5 * SCALE)));
        g.drawRoundRect(x, y, w, h, 10, 10);

        int d = (int) (22 * SCALE);
        int cx = Math.max(1, x - d / 2);
        int cy = Math.max(1, y - d / 2);
        g.fillOval(cx, cy, d, d);
        g.setColor(Color.WHITE);
        String label = String.valueOf(number);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(
                label,
                cx + (d - fm.stringWidth(label)) / 2,
                cy + (d + fm.getAscent() - fm.getDescent()) / 2);
    }

    private static BufferedImage toBufferedImage(WritableImage image) {
        int w = (int) image.getWidth();
        int h = (int) image.getHeight();
        int[] pixels = new int[w * h];
        image.getPixelReader()
                .getPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), pixels, 0, w);
        BufferedImage buffered = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        buffered.setRGB(0, 0, w, h, pixels, 0, w);
        return buffered;
    }

    @SuppressWarnings("unchecked")
    private <T extends Node> T node(String id) {
        Node n = scene.lookup("#" + id);
        if (n == null) {
            throw new IllegalStateException("Node #" + id + " not found");
        }
        return (T) n;
    }
}
