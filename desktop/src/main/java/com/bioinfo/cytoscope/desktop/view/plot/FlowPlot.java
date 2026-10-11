package com.bioinfo.cytoscope.desktop.view.plot;

import static com.bioinfo.cytoscope.desktop.view.plot.PlotGeometry.HEIGHT;
import static com.bioinfo.cytoscope.desktop.view.plot.PlotGeometry.LEFT;
import static com.bioinfo.cytoscope.desktop.view.plot.PlotGeometry.PLOT_SIZE;
import static com.bioinfo.cytoscope.desktop.view.plot.PlotGeometry.TOP;
import static com.bioinfo.cytoscope.desktop.view.plot.PlotGeometry.WIDTH;
import static com.bioinfo.cytoscope.desktop.view.plot.PlotGeometry.clampX;
import static com.bioinfo.cytoscope.desktop.view.plot.PlotGeometry.clampY;
import static com.bioinfo.cytoscope.desktop.view.plot.PlotGeometry.positionX;
import static com.bioinfo.cytoscope.desktop.view.plot.PlotGeometry.positionY;
import static com.bioinfo.cytoscope.desktop.view.plot.PlotGeometry.px;
import static com.bioinfo.cytoscope.desktop.view.plot.PlotGeometry.py;

import com.bioinfo.cytoscope.gating.Gate;
import com.bioinfo.cytoscope.gating.IntervalGate;
import com.bioinfo.cytoscope.gating.PolygonGate;
import com.bioinfo.cytoscope.gating.Quadrant;
import com.bioinfo.cytoscope.gating.QuadrantGate;
import com.bioinfo.cytoscope.gating.RectangleGate;
import com.bioinfo.cytoscope.plot.Charts;
import com.bioinfo.cytoscope.plot.Density;
import com.bioinfo.cytoscope.scale.Scale;

import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.AccessibleRole;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Density plot or histogram of one population, with the gates of its child populations. The user
 * draws new gates with the current {@link PlotTool}, clicks a gate to open its population and
 * drags it to move it; Escape cancels the drawing in progress.
 *
 * <p>The plot keeps no experiment: it reports what the user drew through its callbacks, and the
 * controller gives it the new gates.
 */
public final class FlowPlot extends Region {

    /** Distance, in pixels, under which a click closes a polygon on its first vertex. */
    static final double CLOSE_DISTANCE = 8;

    /** Smallest drag, in pixels, that draws or moves a gate rather than clicking it. */
    static final double MIN_DRAG = 3;

    private static final Color GATE = Color.web("#c2185b");
    private static final Color GATE_TEXT = Color.web("#880e4f");
    private static final Color GATE_REGION = Color.web("#c2185b", 0.06);
    private static final Color DRAFT = Color.web("#0d9488");
    private static final Color AXIS = Color.web("#5f6b76");
    private static final Color TEXT = Color.web("#1d2329");
    private static final Color HISTOGRAM_FILL = Color.web("#99d5cf");
    private static final Color HISTOGRAM_LINE = Color.web("#0f766e");
    private static final Color BORDER = Color.web("#b5bdc5");

    private final Canvas canvas = new Canvas(WIDTH, HEIGHT);
    private PlotData data;
    private List<DrawnGate> gates = List.of();
    private PlotTool tool = PlotTool.SELECT;
    private Consumer<Gate> onCreate = gate -> {};
    private BiConsumer<String, Gate> onMove = (id, gate) -> {};
    private Consumer<String> onOpen = id -> {};

    private Point2D start;
    private Point2D current;
    private boolean dragging;
    private DrawnGate dragged;
    private final List<Point2D> polygon = new ArrayList<>();

    /** Creates an empty plot. */
    public FlowPlot() {
        getStyleClass().add("flow-plot");
        getChildren().add(canvas);
        setFocusTraversable(true);
        setAccessibleRole(AccessibleRole.IMAGE_VIEW);
        setMinSize(WIDTH, HEIGHT);
        setPrefSize(WIDTH, HEIGHT);
        setMaxSize(WIDTH, HEIGHT);
        addEventHandler(MouseEvent.MOUSE_PRESSED, this::onPressed);
        addEventHandler(MouseEvent.MOUSE_DRAGGED, this::onDragged);
        addEventHandler(MouseEvent.MOUSE_RELEASED, this::onReleased);
        addEventHandler(KeyEvent.KEY_PRESSED, this::onKey);
        redraw();
    }

    // ----- Content -----

    /**
     * Shows other events, without gates until {@link #setGates} gives those of the new axes.
     *
     * @param plot the events and scales; null for an empty plot
     */
    public void setPlot(PlotData plot) {
        this.data = plot;
        this.gates = List.of();
        cancelDraft();
    }

    /** Returns the events shown, or null. */
    public PlotData plot() {
        return data;
    }

    /**
     * Shows other gates.
     *
     * @param drawn the gates, on the channels of the plot
     */
    public void setGates(List<DrawnGate> drawn) {
        this.gates = List.copyOf(drawn);
        redraw();
    }

    /** Returns the gates shown. */
    public List<DrawnGate> gates() {
        return gates;
    }

    /**
     * Chooses the drawing tool; the drawing in progress is cancelled.
     *
     * @param newTool the tool
     */
    public void setTool(PlotTool newTool) {
        this.tool = newTool;
        cancelDraft();
    }

    /** Returns the drawing tool. */
    public PlotTool tool() {
        return tool;
    }

    /**
     * Sets what happens when the user draws a gate. A quadrant gate gives the cross of the four
     * regions, as the region below and left of the crossing point.
     *
     * @param action receives the new gate, in data values
     */
    public void setOnCreate(Consumer<Gate> action) {
        this.onCreate = action;
    }

    /**
     * Sets what happens when the user drags a gate to a new place.
     *
     * @param action receives the identifier of the gate and its new region
     */
    public void setOnMove(BiConsumer<String, Gate> action) {
        this.onMove = action;
    }

    /**
     * Sets what happens when the user clicks a gate.
     *
     * @param action receives the identifier of the gate
     */
    public void setOnOpen(Consumer<String> action) {
        this.onOpen = action;
    }

    /** Returns whether a gate is being drawn. */
    boolean drawing() {
        return !polygon.isEmpty() || (dragging && dragged == null);
    }

    // ----- Interaction -----

    private void onPressed(MouseEvent e) {
        requestFocus();
        if (data == null || e.getButton() != MouseButton.PRIMARY) {
            return;
        }
        start = new Point2D(clampX(e.getX()), clampY(e.getY()));
        current = start;
        dragging = false;
        dragged = tool == PlotTool.SELECT ? gateAt(e.getX(), e.getY(), false) : null;
    }

    private void onDragged(MouseEvent e) {
        if (start == null) {
            return;
        }
        current = new Point2D(clampX(e.getX()), clampY(e.getY()));
        dragging = dragging || current.distance(start) >= MIN_DRAG;
        redraw();
    }

    private void onReleased(MouseEvent e) {
        if (start == null) {
            return;
        }
        Point2D from = start;
        Point2D to = new Point2D(clampX(e.getX()), clampY(e.getY()));
        boolean moved = dragging || to.distance(from) >= MIN_DRAG;
        DrawnGate moving = dragged;
        start = null;
        dragging = false;
        dragged = null;
        switch (tool) {
            case SELECT -> select(e, from, to, moved, moving);
            case RECTANGLE -> {
                if (moved) {
                    onCreate.accept(rectangle(from, to));
                }
            }
            case INTERVAL -> {
                if (moved) {
                    onCreate.accept(interval(from, to));
                }
            }
            case QUADRANT -> {
                if (!moved) {
                    onCreate.accept(quadrant(to));
                }
            }
            default -> {
                if (!moved) {
                    addVertex(to, e.getClickCount() >= 2);
                }
            }
        }
        redraw();
    }

    private void select(MouseEvent e, Point2D from, Point2D to, boolean moved, DrawnGate moving) {
        if (moved && moving != null) {
            onMove.accept(
                    moving.id(),
                    PlotGeometry.shifted(
                            moving.gate(),
                            data.xScale(),
                            data.yScale(),
                            positionX(to.getX()) - positionX(from.getX()),
                            data.yScale() == null
                                    ? 0
                                    : positionY(to.getY()) - positionY(from.getY())));
        } else if (!moved) {
            DrawnGate clicked = gateAt(e.getX(), e.getY(), true);
            if (clicked != null) {
                onOpen.accept(clicked.id());
            }
        }
    }

    private void addVertex(Point2D point, boolean doubleClick) {
        boolean closing =
                polygon.size() >= 3
                        && (doubleClick || point.distance(polygon.getFirst()) < CLOSE_DISTANCE);
        if (closing) {
            Gate gate = polygonGate();
            polygon.clear();
            onCreate.accept(gate);
        } else {
            polygon.add(point);
        }
    }

    private void onKey(KeyEvent e) {
        if (e.getCode() == KeyCode.ESCAPE && drawing()) {
            cancelDraft();
            e.consume();
        }
    }

    private void cancelDraft() {
        polygon.clear();
        start = null;
        dragging = false;
        dragged = null;
        redraw();
    }

    /** Returns the topmost gate under a point, or null; locked gates only if asked. */
    private DrawnGate gateAt(double x, double y, boolean withLocked) {
        if (data == null || x < LEFT || x > LEFT + PLOT_SIZE || y < TOP || y > TOP + PLOT_SIZE) {
            return null;
        }
        double vx = xValue(x);
        double vy = data.yScale() == null ? 0 : yValue(y);
        for (int i = gates.size() - 1; i >= 0; i--) {
            DrawnGate g = gates.get(i);
            if ((withLocked || !g.locked()) && g.gate().contains(vx, vy)) {
                return g;
            }
        }
        return null;
    }

    private double xValue(double x) {
        return data.xScale().invert(positionX(x));
    }

    private double yValue(double y) {
        return data.yScale().invert(positionY(y));
    }

    private Gate rectangle(Point2D a, Point2D b) {
        return new RectangleGate(
                data.x().name(),
                data.y().name(),
                xValue(Math.min(a.getX(), b.getX())),
                xValue(Math.max(a.getX(), b.getX())),
                yValue(Math.max(a.getY(), b.getY())),
                yValue(Math.min(a.getY(), b.getY())));
    }

    private Gate interval(Point2D a, Point2D b) {
        return new IntervalGate(
                data.x().name(),
                xValue(Math.min(a.getX(), b.getX())),
                xValue(Math.max(a.getX(), b.getX())));
    }

    private Gate quadrant(Point2D point) {
        return new QuadrantGate(
                data.x().name(),
                data.y().name(),
                xValue(point.getX()),
                yValue(point.getY()),
                Quadrant.NEGATIVE_NEGATIVE);
    }

    private Gate polygonGate() {
        double[] xs = new double[polygon.size()];
        double[] ys = new double[polygon.size()];
        for (int i = 0; i < polygon.size(); i++) {
            xs[i] = xValue(polygon.get(i).getX());
            ys[i] = yValue(polygon.get(i).getY());
        }
        return new PolygonGate(data.x().name(), data.y().name(), xs, ys);
    }

    // ----- Drawing -----

    private void redraw() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setFill(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);
        g.setStroke(BORDER);
        g.setLineWidth(1);
        g.strokeRect(LEFT + 0.5, TOP + 0.5, PLOT_SIZE - 1, PLOT_SIZE - 1);
        if (data == null) {
            return;
        }
        if (data.density() != null) {
            drawDensity(g, data.density());
        } else {
            drawHistogram(g, data.histogram());
        }
        drawAxes(g);
        for (DrawnGate gate : gates) {
            Gate shown = gate.gate();
            if (gate == dragged && dragging) {
                shown =
                        PlotGeometry.shifted(
                                shown,
                                data.xScale(),
                                data.yScale(),
                                positionX(current.getX()) - positionX(start.getX()),
                                data.yScale() == null
                                        ? 0
                                        : positionY(current.getY()) - positionY(start.getY()));
            }
            drawGate(g, gate, shown, gates.indexOf(gate));
        }
        drawDraft(g);
    }

    private void drawDensity(GraphicsContext g, Density density) {
        int bins = density.bins();
        WritableImage image = new WritableImage(bins, bins);
        PixelWriter pixels = image.getPixelWriter();
        double logMax = Math.log1p(Math.max(1, density.max()));
        for (int x = 0; x < bins; x++) {
            for (int y = 0; y < bins; y++) {
                int count = density.count(x, y);
                if (count > 0) {
                    java.awt.Color c = Charts.heat(Math.log1p(count) / logMax);
                    pixels.setArgb(x, bins - 1 - y, 0xff000000 | c.getRGB());
                }
            }
        }
        g.setImageSmoothing(false);
        g.drawImage(image, LEFT, TOP, PLOT_SIZE, PLOT_SIZE);
    }

    private void drawHistogram(GraphicsContext g, int[] counts) {
        int max = 1;
        for (int count : counts) {
            max = Math.max(max, count);
        }
        double width = PLOT_SIZE / counts.length;
        g.beginPath();
        g.moveTo(LEFT, TOP + PLOT_SIZE);
        for (int i = 0; i < counts.length; i++) {
            double y = TOP + PLOT_SIZE * (1 - 0.95 * counts[i] / max);
            g.lineTo(LEFT + i * width, y);
            g.lineTo(LEFT + (i + 1) * width, y);
        }
        g.lineTo(LEFT + PLOT_SIZE, TOP + PLOT_SIZE);
        g.closePath();
        g.setFill(HISTOGRAM_FILL);
        g.fill();
        g.setStroke(HISTOGRAM_LINE);
        g.setLineWidth(1);
        g.stroke();
    }

    private void drawAxes(GraphicsContext g) {
        g.setFont(Font.font(11));
        g.setStroke(AXIS);
        g.setFill(TEXT);
        g.setLineWidth(1);
        Scale xs = data.xScale();
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.TOP);
        List<Double> xLabels = PlotGeometry.labeledTicks(xs, true);
        for (double tick : xs.ticks()) {
            double x = px(xs.apply(tick));
            g.strokeLine(x, TOP + PLOT_SIZE, x, TOP + PLOT_SIZE + 5);
            if (xLabels.contains(tick)) {
                g.fillText(Charts.tickLabel(tick, xs), x, TOP + PLOT_SIZE + 7);
            }
        }
        g.setFont(Font.font(null, FontWeight.BOLD, 12));
        g.setTextBaseline(VPos.BOTTOM);
        g.fillText(data.x().displayName(), LEFT + PLOT_SIZE / 2, HEIGHT - 4);
        if (data.yScale() == null) {
            return;
        }
        Scale ys = data.yScale();
        g.setFont(Font.font(11));
        g.setTextAlign(TextAlignment.RIGHT);
        g.setTextBaseline(VPos.CENTER);
        List<Double> yLabels = PlotGeometry.labeledTicks(ys, false);
        for (double tick : ys.ticks()) {
            double y = py(ys.apply(tick));
            g.strokeLine(LEFT - 5, y, LEFT, y);
            if (yLabels.contains(tick)) {
                g.fillText(Charts.tickLabel(tick, ys), LEFT - 8, y);
            }
        }
        g.save();
        g.translate(14, TOP + PLOT_SIZE / 2);
        g.rotate(-90);
        g.setFont(Font.font(null, FontWeight.BOLD, 12));
        g.setTextAlign(TextAlignment.CENTER);
        g.fillText(data.y().displayName(), 0, 0);
        g.restore();
    }

    private void drawGate(GraphicsContext g, DrawnGate drawn, Gate gate, int index) {
        g.setStroke(GATE);
        g.setLineWidth(1.5);
        g.setLineCap(StrokeLineCap.BUTT);
        g.setFont(Font.font(null, FontWeight.BOLD, 11));
        Scale xs = data.xScale();
        Scale ys = data.yScale();
        switch (gate) {
            case RectangleGate r -> {
                double left = clampX(px(xs.apply(r.xMin())));
                double right = clampX(px(xs.apply(r.xMax())));
                double top = clampY(py(ys.apply(r.yMax())));
                double bottom = clampY(py(ys.apply(r.yMin())));
                g.strokeRect(left, top, right - left, bottom - top);
                label(g, drawn.label(), left + 3, top - 4, TextAlignment.LEFT, VPos.BOTTOM);
            }
            case PolygonGate p -> {
                double[] xs2 = p.xs();
                double[] ys2 = p.ys();
                double[] x = new double[xs2.length];
                double[] y = new double[ys2.length];
                double top = Double.MAX_VALUE;
                double left = Double.MAX_VALUE;
                for (int i = 0; i < x.length; i++) {
                    x[i] = clampX(px(xs.apply(xs2[i])));
                    y[i] = clampY(py(ys.apply(ys2[i])));
                    top = Math.min(top, y[i]);
                    left = Math.min(left, x[i]);
                }
                g.strokePolygon(x, y, x.length);
                label(g, drawn.label(), left + 3, top - 4, TextAlignment.LEFT, VPos.BOTTOM);
            }
            case QuadrantGate q -> drawQuadrant(g, drawn, q);
            case IntervalGate i -> {
                double left = clampX(px(xs.apply(i.min())));
                double right = clampX(px(xs.apply(i.max())));
                double bar = TOP + 14 + (index % 6) * 16;
                g.setFill(GATE_REGION);
                g.fillRect(left, TOP, right - left, PLOT_SIZE);
                g.strokeLine(left, bar, right, bar);
                g.strokeLine(left, bar - 5, left, bar + 5);
                g.strokeLine(right, bar - 5, right, bar + 5);
                label(g, drawn.label(), (left + right) / 2, bar - 3, TextAlignment.CENTER,
                        VPos.BOTTOM);
            }
        }
    }

    private void drawQuadrant(GraphicsContext g, DrawnGate drawn, QuadrantGate q) {
        double cx = clampX(px(data.xScale().apply(q.xThreshold())));
        double cy = clampY(py(data.yScale().apply(q.yThreshold())));
        boolean right = q.quadrant().xPositive();
        boolean upper = q.quadrant().yPositive();
        g.setFill(GATE_REGION);
        g.fillRect(
                right ? cx : LEFT,
                upper ? TOP : cy,
                right ? LEFT + PLOT_SIZE - cx : cx - LEFT,
                upper ? cy - TOP : TOP + PLOT_SIZE - cy);
        g.strokeLine(cx, TOP, cx, TOP + PLOT_SIZE);
        g.strokeLine(LEFT, cy, LEFT + PLOT_SIZE, cy);
        label(
                g,
                drawn.label(),
                right ? LEFT + PLOT_SIZE - 4 : LEFT + 4,
                upper ? TOP + 4 : TOP + PLOT_SIZE - 4,
                right ? TextAlignment.RIGHT : TextAlignment.LEFT,
                upper ? VPos.TOP : VPos.BOTTOM);
    }

    /** Writes the name of a gate, with a white outline that keeps it readable on the events. */
    private static void label(
            GraphicsContext g, String text, double x, double y, TextAlignment align, VPos base) {
        g.setTextAlign(align);
        g.setTextBaseline(base);
        g.setStroke(Color.WHITE);
        g.setLineWidth(3);
        g.strokeText(text, x, y);
        g.setFill(GATE_TEXT);
        g.fillText(text, x, y);
        g.setStroke(GATE);
        g.setLineWidth(1.5);
    }

    private void drawDraft(GraphicsContext g) {
        g.setStroke(DRAFT);
        g.setLineWidth(1.5);
        g.setLineDashes(4, 3);
        g.setFill(Color.web("#0d9488", 0.1));
        if (dragging && dragged == null && start != null) {
            double left = Math.min(start.getX(), current.getX());
            double width = Math.abs(current.getX() - start.getX());
            if (tool == PlotTool.INTERVAL) {
                g.fillRect(left, TOP, width, PLOT_SIZE);
                g.strokeRect(left, TOP, width, PLOT_SIZE);
            } else if (tool == PlotTool.RECTANGLE) {
                double top = Math.min(start.getY(), current.getY());
                double height = Math.abs(current.getY() - start.getY());
                g.fillRect(left, top, width, height);
                g.strokeRect(left, top, width, height);
            }
        }
        if (!polygon.isEmpty()) {
            double[] x = polygon.stream().mapToDouble(Point2D::getX).toArray();
            double[] y = polygon.stream().mapToDouble(Point2D::getY).toArray();
            g.strokePolyline(x, y, x.length);
        }
        g.setLineDashes();
    }
}
