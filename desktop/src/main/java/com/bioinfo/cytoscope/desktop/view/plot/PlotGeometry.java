package com.bioinfo.cytoscope.desktop.view.plot;

import com.bioinfo.cytoscope.gating.Gate;
import com.bioinfo.cytoscope.gating.IntervalGate;
import com.bioinfo.cytoscope.gating.PolygonGate;
import com.bioinfo.cytoscope.gating.QuadrantGate;
import com.bioinfo.cytoscope.gating.RectangleGate;
import com.bioinfo.cytoscope.scale.Scale;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

/**
 * Layout of the plots, as pure functions so that they can be tested without a window. Positions
 * on an axis go from 0 to 1; the plot area is {@link #PLOT_SIZE} pixels wide, inside margins
 * for the ticks and the axis titles.
 */
public final class PlotGeometry {

    /** Side of the plot area, in pixels. */
    public static final double PLOT_SIZE = 320;

    /** Margin on the left, for the ticks and the title of the vertical axis. */
    public static final double LEFT = 58;

    /** Margin on the right. */
    public static final double RIGHT = 12;

    /** Margin above the plot area. */
    public static final double TOP = 12;

    /** Margin below the plot area, for the ticks and the title of the horizontal axis. */
    public static final double BOTTOM = 44;

    /** Width of a plot. */
    public static final double WIDTH = LEFT + PLOT_SIZE + RIGHT;

    /** Height of a plot. */
    public static final double HEIGHT = TOP + PLOT_SIZE + BOTTOM;

    /** Smallest distance, in pixels, between two tick labels of the horizontal axis. */
    static final double MIN_LABEL_GAP_X = 30;

    /** Smallest distance, in pixels, between two tick labels of the vertical axis. */
    static final double MIN_LABEL_GAP_Y = 14;

    private PlotGeometry() {}

    /** Returns the abscissa of a position on the horizontal axis. */
    static double px(double position) {
        return LEFT + position * PLOT_SIZE;
    }

    /** Returns the ordinate of a position on the vertical axis, which goes upward. */
    static double py(double position) {
        return TOP + (1 - position) * PLOT_SIZE;
    }

    /** Returns the position on the horizontal axis of an abscissa. */
    static double positionX(double x) {
        return (x - LEFT) / PLOT_SIZE;
    }

    /** Returns the position on the vertical axis of an ordinate. */
    static double positionY(double y) {
        return 1 - (y - TOP) / PLOT_SIZE;
    }

    /** Keeps an abscissa inside the plot area. */
    static double clampX(double x) {
        return Math.max(LEFT, Math.min(LEFT + PLOT_SIZE, x));
    }

    /** Keeps an ordinate inside the plot area. */
    static double clampY(double y) {
        return Math.max(TOP, Math.min(TOP + PLOT_SIZE, y));
    }

    /**
     * Returns the ticks of a scale whose label is drawn. Near zero on a logicle or arcsinh axis,
     * the decades of both signs are drawn close together: zero, then the largest values, keep
     * their label, and a label that would overlap one already kept is skipped.
     *
     * @param scale the scale of the axis
     * @param horizontal whether the axis is horizontal, whose labels are wider
     * @return the values of the labeled ticks
     */
    static List<Double> labeledTicks(Scale scale, boolean horizontal) {
        double gap = (horizontal ? MIN_LABEL_GAP_X : MIN_LABEL_GAP_Y) / PLOT_SIZE;
        List<Double> byPriority = new ArrayList<>();
        for (double tick : scale.ticks()) {
            byPriority.add(tick);
        }
        byPriority.sort(
                Comparator.comparing((Double t) -> t != 0).thenComparing(t -> -Math.abs(t)));
        List<Double> kept = new ArrayList<>();
        for (double tick : byPriority) {
            double position = scale.apply(tick);
            if (kept.stream().allMatch(k -> Math.abs(scale.apply(k) - position) >= gap)) {
                kept.add(tick);
            }
        }
        return kept;
    }

    /**
     * Moves a gate by a distance measured in axis positions, so that it keeps its shape on the
     * scales shown.
     *
     * @param gate the gate
     * @param xScale scale of the horizontal axis
     * @param yScale scale of the vertical axis; null for a histogram
     * @param dx move along the horizontal axis, in positions
     * @param dy move along the vertical axis, in positions
     * @return the moved gate
     */
    static Gate shifted(Gate gate, Scale xScale, Scale yScale, double dx, double dy) {
        DoubleUnaryOperator fx = v -> xScale.invert(xScale.apply(v) + dx);
        DoubleUnaryOperator fy = v -> yScale.invert(yScale.apply(v) + dy);
        return switch (gate) {
            case RectangleGate r ->
                    new RectangleGate(
                            r.xChannel(),
                            r.yChannel(),
                            fx.applyAsDouble(r.xMin()),
                            fx.applyAsDouble(r.xMax()),
                            fy.applyAsDouble(r.yMin()),
                            fy.applyAsDouble(r.yMax()));
            case PolygonGate p -> {
                double[] xs = p.xs();
                double[] ys = p.ys();
                for (int i = 0; i < xs.length; i++) {
                    xs[i] = fx.applyAsDouble(xs[i]);
                    ys[i] = fy.applyAsDouble(ys[i]);
                }
                yield new PolygonGate(p.xChannel(), p.yChannel(), xs, ys);
            }
            case QuadrantGate q ->
                    q.withThresholds(
                            fx.applyAsDouble(q.xThreshold()), fy.applyAsDouble(q.yThreshold()));
            case IntervalGate i ->
                    new IntervalGate(
                            i.xChannel(), fx.applyAsDouble(i.min()), fx.applyAsDouble(i.max()));
        };
    }
}
