package com.bioinfo.cytoscope.desktop.view.plot;

import static com.bioinfo.cytoscope.desktop.view.plot.PlotGeometry.LEFT;
import static com.bioinfo.cytoscope.desktop.view.plot.PlotGeometry.PLOT_SIZE;
import static com.bioinfo.cytoscope.desktop.view.plot.PlotGeometry.TOP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bioinfo.cytoscope.desktop.FxThread;
import com.bioinfo.cytoscope.experiment.Experiment;
import com.bioinfo.cytoscope.gating.Gate;
import com.bioinfo.cytoscope.gating.GatingResult;
import com.bioinfo.cytoscope.gating.IntervalGate;
import com.bioinfo.cytoscope.gating.PolygonGate;
import com.bioinfo.cytoscope.gating.Population;
import com.bioinfo.cytoscope.gating.Quadrant;
import com.bioinfo.cytoscope.gating.QuadrantGate;
import com.bioinfo.cytoscope.gating.RectangleGate;
import com.bioinfo.cytoscope.scale.ScaleType;
import com.bioinfo.cytoscope.service.CytoService;
import com.bioinfo.cytoscope.service.Example;
import com.bioinfo.cytoscope.service.ExampleLibrary;
import com.bioinfo.cytoscope.service.InMemorySampleStore;

import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Tests of the drawing and of the mouse gestures of a plot, on headless JavaFX. */
class FlowPlotTest {

    private static GatingResult gating;

    private final List<Gate> created = new ArrayList<>();
    private final List<String> opened = new ArrayList<>();
    private final List<String> movedIds = new ArrayList<>();
    private final List<Gate> moved = new ArrayList<>();
    private FlowPlot plot;

    @BeforeAll
    static void gateAnExample() {
        FxThread.start();
        CytoService service = new CytoService();
        AtomicInteger next = new AtomicInteger();
        ExampleLibrary.OpenedExample opened =
                new ExampleLibrary(service)
                        .open(
                                Example.CADMIUM,
                                "All events",
                                ExampleLibrary.PresetNames.ENGLISH,
                                () -> "s" + next.incrementAndGet());
        InMemorySampleStore store = new InMemorySampleStore();
        store.putAll(opened.samples());
        Experiment experiment = opened.experiment();
        gating = service.gate(store, experiment, experiment.samples().getFirst().id());
    }

    @BeforeEach
    void createPlot() {
        plot =
                FxThread.call(
                        () -> {
                            FlowPlot p = new FlowPlot();
                            p.setOnCreate(created::add);
                            p.setOnOpen(opened::add);
                            p.setOnMove(
                                    (id, gate) -> {
                                        movedIds.add(id);
                                        moved.add(gate);
                                    });
                            return p;
                        });
    }

    private static PlotData dotPlot() {
        return PlotData.of(
                gating, Population.ROOT_ID, "FSC-A", "SSC-A", ScaleType.LINEAR, null, null);
    }

    private static PlotData histogram() {
        return PlotData.of(gating, Population.ROOT_ID, "PI-A", null, null, null, "PI-A");
    }

    private void show(PlotData data, PlotTool tool, DrawnGate... gates) {
        FxThread.run(
                () -> {
                    plot.setPlot(data);
                    plot.setTool(tool);
                    plot.setGates(List.of(gates));
                });
    }

    private void mouse(
            EventType<MouseEvent> type,
            double x,
            double y,
            MouseButton button,
            int clicks) {
        FxThread.run(
                () ->
                        Event.fireEvent(
                                plot,
                                new MouseEvent(
                                        type, x, y, x, y, button, clicks, false, false, false,
                                        false, button == MouseButton.PRIMARY, false,
                                        button == MouseButton.SECONDARY, false, false, false,
                                        null)));
    }

    private void click(double x, double y) {
        click(x, y, 1);
    }

    private void click(double x, double y, int clicks) {
        mouse(MouseEvent.MOUSE_PRESSED, x, y, MouseButton.PRIMARY, clicks);
        mouse(MouseEvent.MOUSE_RELEASED, x, y, MouseButton.PRIMARY, clicks);
    }

    private void drag(double x1, double y1, double x2, double y2) {
        mouse(MouseEvent.MOUSE_PRESSED, x1, y1, MouseButton.PRIMARY, 1);
        mouse(MouseEvent.MOUSE_DRAGGED, (x1 + x2) / 2, (y1 + y2) / 2, MouseButton.PRIMARY, 1);
        mouse(MouseEvent.MOUSE_DRAGGED, x2, y2, MouseButton.PRIMARY, 1);
        mouse(MouseEvent.MOUSE_RELEASED, x2, y2, MouseButton.PRIMARY, 1);
    }

    private boolean escape() {
        KeyEvent key =
                new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false,
                        false);
        return FxThread.call(
                () -> {
                    List<Boolean> consumed = new ArrayList<>();
                    EventHandler<KeyEvent> spy = e -> consumed.add(e.isConsumed());
                    plot.addEventHandler(KeyEvent.KEY_PRESSED, spy);
                    Event.fireEvent(plot, key);
                    plot.removeEventHandler(KeyEvent.KEY_PRESSED, spy);
                    return consumed.getFirst();
                });
    }

    private static double x(PlotData data, double value) {
        return PlotGeometry.px(data.xScale().apply(value));
    }

    private static double y(PlotData data, double value) {
        return PlotGeometry.py(data.yScale().apply(value));
    }

    @Test
    void anEmptyPlotIgnoresTheMouse() {
        assertNull(FxThread.call(plot::plot));
        click(150, 150);
        drag(100, 100, 200, 200);
        mouse(MouseEvent.MOUSE_DRAGGED, 120, 120, MouseButton.PRIMARY, 1);
        mouse(MouseEvent.MOUSE_RELEASED, 120, 120, MouseButton.PRIMARY, 1);
        assertTrue(created.isEmpty() && opened.isEmpty() && moved.isEmpty());
    }

    @Test
    void rectangleIsDrawnByDragging() {
        PlotData data = dotPlot();
        show(data, PlotTool.RECTANGLE);
        assertEquals(PlotTool.RECTANGLE, FxThread.call(plot::tool));
        click(150, 150);
        assertTrue(created.isEmpty(), "a click draws nothing");

        mouse(MouseEvent.MOUSE_PRESSED, 300, 100, MouseButton.PRIMARY, 1);
        mouse(MouseEvent.MOUSE_DRAGGED, 200, 200, MouseButton.PRIMARY, 1);
        assertTrue(FxThread.call(plot::drawing));
        mouse(MouseEvent.MOUSE_RELEASED, 100, 250, MouseButton.PRIMARY, 1);

        RectangleGate gate = assertInstanceOf(RectangleGate.class, created.getFirst());
        assertEquals("FSC-A", gate.xChannel());
        assertEquals("SSC-A", gate.yChannel());
        assertEquals(data.xScale().invert(PlotGeometry.positionX(100)), gate.xMin(), 1e-6);
        assertEquals(data.xScale().invert(PlotGeometry.positionX(300)), gate.xMax(), 1e-6);
        assertTrue(gate.yMin() < gate.yMax());
        assertFalse(FxThread.call(plot::drawing));
    }

    @Test
    void dragsOutsideThePlotAreClamped() {
        show(dotPlot(), PlotTool.RECTANGLE);
        drag(0, 0, 1000, 1000);
        RectangleGate gate = assertInstanceOf(RectangleGate.class, created.getFirst());
        PlotData data = dotPlot();
        assertEquals(data.xScale().invert(0), gate.xMin(), 1e-6);
        assertEquals(data.xScale().invert(1), gate.xMax(), 1e-6);
    }

    @Test
    void intervalIsDrawnOnAHistogram() {
        PlotData data = histogram();
        show(data, PlotTool.INTERVAL);
        click(150, 150);
        drag(250, 200, 120, 200);
        IntervalGate gate = assertInstanceOf(IntervalGate.class, created.getFirst());
        assertEquals("PI-A", gate.xChannel());
        assertEquals(data.xScale().invert(PlotGeometry.positionX(120)), gate.min(), 1e-6);
        assertEquals(data.xScale().invert(PlotGeometry.positionX(250)), gate.max(), 1e-6);
        assertEquals(1, created.size());
    }

    @Test
    void quadrantsArePlacedByAClick() {
        PlotData data = dotPlot();
        show(data, PlotTool.QUADRANT);
        drag(100, 100, 200, 200);
        assertTrue(created.isEmpty(), "a drag places no cross");
        click(200, 150);
        QuadrantGate gate = assertInstanceOf(QuadrantGate.class, created.getFirst());
        assertEquals(Quadrant.NEGATIVE_NEGATIVE, gate.quadrant());
        assertEquals(data.xScale().invert(PlotGeometry.positionX(200)), gate.xThreshold(), 1e-6);
        assertEquals(data.yScale().invert(PlotGeometry.positionY(150)), gate.yThreshold(), 1e-6);
    }

    @Test
    void polygonClosesOnItsFirstVertex() {
        show(dotPlot(), PlotTool.POLYGON);
        click(100, 100);
        click(300, 100);
        assertTrue(FxThread.call(plot::drawing));
        click(102, 101);
        assertTrue(created.isEmpty(), "two vertices do not make a polygon");
        drag(200, 200, 260, 260);
        click(200, 300);
        click(103, 102);
        PolygonGate gate = assertInstanceOf(PolygonGate.class, created.getFirst());
        assertEquals(4, gate.xs().length);
        assertFalse(FxThread.call(plot::drawing));
    }

    @Test
    void polygonClosesOnADoubleClick() {
        show(dotPlot(), PlotTool.POLYGON);
        click(100, 100);
        click(300, 100);
        click(300, 300);
        click(100, 300, 2);
        assertEquals(3, assertInstanceOf(PolygonGate.class, created.getFirst()).xs().length);
    }

    @Test
    void escapeCancelsTheDrawing() {
        show(dotPlot(), PlotTool.POLYGON);
        assertFalse(escape(), "nothing to cancel: the key goes on");
        click(100, 100);
        click(200, 100);
        assertTrue(escape());
        assertFalse(FxThread.call(plot::drawing));
        click(150, 150);
        click(250, 150);
        FxThread.run(() -> plot.setTool(PlotTool.RECTANGLE));
        assertFalse(FxThread.call(plot::drawing), "changing the tool cancels too");
    }

    @Test
    void selectOpensAndMovesTheGates() {
        PlotData data = dotPlot();
        RectangleGate cells = new RectangleGate("FSC-A", "SSC-A", 50_000, 150_000, 20_000, 80_000);
        DrawnGate drawn = new DrawnGate("p1", "Cells", cells, "84.0%", false);
        show(data, PlotTool.SELECT, drawn);
        assertEquals(List.of(drawn), FxThread.call(plot::gates));
        double cx = x(data, 100_000);
        double cy = y(data, 50_000);

        click(cx, cy);
        assertEquals(List.of("p1"), opened);
        click(LEFT + PLOT_SIZE - 2, TOP + 2);
        click(LEFT - 20, cy);
        mouse(MouseEvent.MOUSE_PRESSED, cx, cy, MouseButton.SECONDARY, 1);
        mouse(MouseEvent.MOUSE_RELEASED, cx, cy, MouseButton.SECONDARY, 1);
        assertEquals(List.of("p1"), opened, "outside the gate or with another button");

        drag(cx, cy, cx + 32, cy - 32);
        assertEquals(List.of("p1"), movedIds);
        RectangleGate shifted = assertInstanceOf(RectangleGate.class, moved.getFirst());
        double dx = data.xScale().invert(data.xScale().apply(50_000) + 0.1) - 50_000;
        assertEquals(50_000 + dx, shifted.xMin(), 1e-3);
        assertTrue(shifted.yMin() > cells.yMin());

        drag(LEFT + 5, TOP + 5, LEFT + 50, TOP + 50);
        assertEquals(1, moved.size(), "dragging outside the gates moves nothing");
        assertTrue(created.isEmpty());
    }

    @Test
    void lockedGatesOpenButDoNotMove() {
        PlotData data = histogram();
        IntervalGate g1 = new IntervalGate("PI-A", 0, data.xScale().invert(0.5));
        show(
                data,
                PlotTool.SELECT,
                new DrawnGate("phase", "G0/G1", g1, null, true),
                new DrawnGate("other", "S", new IntervalGate("PI-A", 0, 1), "", true));
        double inside = PlotGeometry.px(0.25);
        drag(inside, 200, inside + 40, 200);
        assertTrue(moved.isEmpty());
        click(inside, 200);
        assertEquals(List.of("phase"), opened);
    }

    @Test
    void histogramGatesMoveAlongTheAxisOnly() {
        PlotData data = histogram();
        IntervalGate g1 =
                new IntervalGate("PI-A", data.xScale().invert(0.2), data.xScale().invert(0.6));
        show(data, PlotTool.SELECT, new DrawnGate("i", "G1", g1, "", false));
        drag(PlotGeometry.px(0.4), 200, PlotGeometry.px(0.5), 150);
        IntervalGate shifted = assertInstanceOf(IntervalGate.class, moved.getFirst());
        assertEquals(data.xScale().invert(0.3), shifted.min(), 1e-3);
    }

    @Test
    void everyKindOfGateIsDrawn() {
        PlotData data = dotPlot();
        Gate polygon =
                new PolygonGate(
                        "FSC-A",
                        "SSC-A",
                        new double[] {40_000, 120_000, 90_000},
                        new double[] {10_000, 20_000, 90_000});
        List<DrawnGate> gates = new ArrayList<>();
        for (Quadrant q : Quadrant.values()) {
            gates.add(
                    new DrawnGate(
                            q.name(),
                            q.name(),
                            new QuadrantGate("FSC-A", "SSC-A", 100_000, 50_000, q),
                            "",
                            false));
        }
        gates.add(new DrawnGate("poly", "Polygon", polygon, "1%", false));
        show(data, PlotTool.SELECT, gates.toArray(DrawnGate[]::new));
        double px = x(data, 80_000);
        double py = y(data, 40_000);
        mouse(MouseEvent.MOUSE_PRESSED, px, py, MouseButton.PRIMARY, 1);
        mouse(MouseEvent.MOUSE_DRAGGED, px + 20, py, MouseButton.PRIMARY, 1);
        mouse(MouseEvent.MOUSE_RELEASED, px + 20, py, MouseButton.PRIMARY, 1);
        assertEquals("poly", movedIds.getFirst(), "the last gate drawn is picked first");

        PlotData logicle =
                PlotData.of(
                        gating, Population.ROOT_ID, "FITC-A", "PI-A", ScaleType.LOGICLE,
                        ScaleType.ARCSINH, null);
        FxThread.run(() -> plot.setPlot(logicle));
        assertSame(logicle, FxThread.call(plot::plot));
    }

    @Test
    void geometryHelpers() {
        assertEquals(LEFT, PlotGeometry.clampX(-5));
        assertEquals(LEFT + PLOT_SIZE, PlotGeometry.clampX(5000));
        assertEquals(TOP, PlotGeometry.clampY(-5));
        assertEquals(TOP + PLOT_SIZE, PlotGeometry.clampY(5000));
        assertEquals(0.25, PlotGeometry.positionX(PlotGeometry.px(0.25)), 1e-12);
        assertEquals(0.75, PlotGeometry.positionY(PlotGeometry.py(0.75)), 1e-12);

        PlotData logicle =
                PlotData.of(
                        gating, Population.ROOT_ID, "FITC-A", "PI-A", ScaleType.LOGICLE,
                        ScaleType.LOGICLE, null);
        List<Double> labels = PlotGeometry.labeledTicks(logicle.xScale(), true);
        assertEquals(0.0, labels.getFirst());
        for (int i = 0; i < labels.size(); i++) {
            for (int j = i + 1; j < labels.size(); j++) {
                double gap =
                        Math.abs(
                                logicle.xScale().apply(labels.get(i))
                                        - logicle.xScale().apply(labels.get(j)));
                assertTrue(gap * PLOT_SIZE >= PlotGeometry.MIN_LABEL_GAP_X - 1e-9);
            }
        }
        assertTrue(
                PlotGeometry.labeledTicks(logicle.yScale(), false).size()
                        >= labels.size());
    }

    @Test
    void plotDataOfHistogramsAndErrors() {
        PlotData data = histogram();
        assertNull(data.y());
        assertNull(data.density());
        int[] bars = data.histogram();
        bars[0] = -1;
        assertTrue(data.histogram()[0] >= 0, "the histogram is copied");
        assertEquals(PlotData.HISTOGRAM_BINS, bars.length);
        assertNull(dotPlot().histogram());
        assertThrows(
                IllegalArgumentException.class,
                () -> PlotData.of(gating, "nope", "FSC-A", null, null, null, null));
        IntervalGate interval = new IntervalGate("PI-A", 0, 1);
        assertThrows(
                NullPointerException.class, () -> new DrawnGate(null, "x", interval, "", false));
        assertEquals("Cells", new DrawnGate("a", "Cells", interval, null, false).label());
        assertEquals("Cells 5%", new DrawnGate("a", "Cells", interval, "5%", false).label());
        assertTrue(PlotTool.INTERVAL.availableOn(true));
        assertFalse(PlotTool.RECTANGLE.availableOn(true));
        assertTrue(PlotTool.SELECT.availableOn(true) && PlotTool.SELECT.availableOn(false));
    }
}
