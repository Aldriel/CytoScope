package com.bioinfo.cytoscope.server.api;

import com.bioinfo.cytoscope.gating.Gate;
import com.bioinfo.cytoscope.gating.IntervalGate;
import com.bioinfo.cytoscope.gating.PolygonGate;
import com.bioinfo.cytoscope.gating.Quadrant;
import com.bioinfo.cytoscope.gating.QuadrantGate;
import com.bioinfo.cytoscope.gating.RectangleGate;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * A gate in JSON, told apart by its {@code type}: {@code rectangle}, {@code polygon},
 * {@code quadrant} or {@code interval}. The coordinates are data values, not plot positions.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = GateJson.Rectangle.class, name = "rectangle"),
    @JsonSubTypes.Type(value = GateJson.Polygon.class, name = "polygon"),
    @JsonSubTypes.Type(value = GateJson.QuadrantRegion.class, name = "quadrant"),
    @JsonSubTypes.Type(value = GateJson.Interval.class, name = "interval")
})
public sealed interface GateJson {

    /**
     * Converts the JSON form to the gate.
     *
     * @return the gate
     * @throws IllegalArgumentException if the gate is not valid
     */
    Gate toGate();

    /**
     * Converts a gate to its JSON form.
     *
     * @param gate the gate
     * @return the JSON form
     */
    static GateJson of(Gate gate) {
        return switch (gate) {
            case RectangleGate r -> new Rectangle(r.xChannel(), r.yChannel(), r.xMin(), r.xMax(),
                    r.yMin(), r.yMax());
            case PolygonGate p -> new Polygon(p.xChannel(), p.yChannel(), p.xs(), p.ys());
            case QuadrantGate q -> new QuadrantRegion(q.xChannel(), q.yChannel(), q.xThreshold(),
                    q.yThreshold(), q.quadrant());
            case IntervalGate i -> new Interval(i.xChannel(), i.min(), i.max());
        };
    }

    /**
     * A rectangle on two channels.
     *
     * @param xChannel horizontal channel
     * @param yChannel vertical channel
     * @param xMin left edge
     * @param xMax right edge
     * @param yMin bottom edge
     * @param yMax top edge
     */
    record Rectangle(String xChannel, String yChannel, double xMin, double xMax, double yMin,
                     double yMax) implements GateJson {

        @Override
        public Gate toGate() {
            return new RectangleGate(xChannel, yChannel, xMin, xMax, yMin, yMax);
        }
    }

    /**
     * A polygon on two channels.
     *
     * @param xChannel horizontal channel
     * @param yChannel vertical channel
     * @param xs horizontal coordinates of the vertices
     * @param ys vertical coordinates of the vertices
     */
    record Polygon(String xChannel, String yChannel, double[] xs, double[] ys)
            implements GateJson {

        @Override
        public Gate toGate() {
            if (xs == null || ys == null) {
                throw new IllegalArgumentException("A polygon needs its vertices");
            }
            return new PolygonGate(xChannel, yChannel, xs, ys);
        }
    }

    /**
     * One region of a quadrant gate.
     *
     * @param xChannel horizontal channel
     * @param yChannel vertical channel
     * @param xThreshold vertical line of the cross
     * @param yThreshold horizontal line of the cross
     * @param quadrant the region
     */
    record QuadrantRegion(String xChannel, String yChannel, double xThreshold,
                          double yThreshold, Quadrant quadrant) implements GateJson {

        @Override
        public Gate toGate() {
            return new QuadrantGate(xChannel, yChannel, xThreshold, yThreshold, quadrant);
        }
    }

    /**
     * An interval on one channel.
     *
     * @param xChannel the channel
     * @param min lower bound
     * @param max upper bound
     */
    record Interval(String xChannel, double min, double max) implements GateJson {

        @Override
        public Gate toGate() {
            return new IntervalGate(xChannel, min, max);
        }
    }
}
