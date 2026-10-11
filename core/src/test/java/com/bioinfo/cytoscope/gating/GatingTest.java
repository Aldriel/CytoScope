package com.bioinfo.cytoscope.gating;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bioinfo.cytoscope.fcs.Channel;
import com.bioinfo.cytoscope.fcs.FcsSample;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GatingTest {

    private static final List<String> QUADRANT_NAMES = List.of("--", "+-", "++", "-+");

    /** Six events: two debris, then cells in each quadrant of FITC / PI. */
    static FcsSample sample() {
        return new FcsSample("tube", Map.of(),
                List.of(new Channel("FSC-A", "", 1000), new Channel("FITC-A", "Annexin V", 1000),
                        new Channel("PI-A", "", 1000)),
                new float[][] {
                    {10, 20, 500, 600, 700, 800},
                    {5, 900, 50, 500, 600, 50},
                    {5, 900, 50, 40, 700, 800}});
    }

    static GatingTree tree() {
        GatingTree tree = GatingTree.create("All")
                .add(Population.ROOT_ID, "Cells", new IntervalGate("FSC-A", 900, 100));
        return tree.addQuadrants("p1", QUADRANT_NAMES, "FITC-A", "PI-A", 100, 100);
    }

    @Test
    void gatesTheHierarchy() {
        GatingResult result = GatingEngine.apply(sample(), tree());
        assertEquals(6, result.count("all"));
        assertEquals(4, result.count("p1"));
        assertEquals(1, result.count("p2"));
        assertEquals(1, result.count("p3"));
        assertEquals(1, result.count("p4"));
        assertEquals(1, result.count("p5"));
        assertEquals(25, result.percentOfParent("p4"));
        assertEquals(100, result.percentOfParent("all"));
        assertEquals(100.0 * 4 / 6, result.percentOfTotal("p1"), 1e-12);
        assertArrayEquals(new double[] {500, 600, 700, 800}, result.values("p1", 0));
        assertEquals(650, result.median("p1", 0));
        assertTrue(result.events("p4").get(4));
        assertEquals(sample().name(), result.sample().name());
        assertEquals(tree(), result.tree());
        assertThrows(IllegalArgumentException.class, () -> result.count("p9"));
        assertThrows(IllegalArgumentException.class, () -> result.percentOfParent("p9"));
    }

    @Test
    void emptyParentGivesNaN() {
        GatingTree tree = GatingTree.create("All")
                .add("all", "None", new RectangleGate("FSC-A", "PI-A", 2000, 3000, 0, 1))
                .add("p1", "Child", new IntervalGate("FITC-A", 0, 1000));
        GatingResult result = GatingEngine.apply(sample(), tree);
        assertTrue(Double.isNaN(result.percentOfParent("p2")));
        FcsSample empty = new FcsSample("e", Map.of(), sample().channels(),
                new float[][] {{}, {}, {}});
        assertTrue(Double.isNaN(GatingEngine.apply(empty, tree).percentOfTotal("p1")));
    }

    @Test
    void treeEditing() {
        GatingTree tree = tree();
        assertEquals("p6", tree.nextId());
        GatingTree renamed = tree.rename("p1", "Lymphocytes");
        assertEquals("Lymphocytes", renamed.find("p1").orElseThrow().name());
        GatingTree removed = tree.remove("p1");
        assertTrue(removed.find("p3").isEmpty());
        assertEquals("p6", removed.nextId());
        assertThrows(IllegalArgumentException.class, () -> tree.remove("all"));
        assertThrows(IllegalArgumentException.class, () -> tree.remove("p42"));
        assertThrows(IllegalArgumentException.class, () -> tree.add("p42", "x",
                new IntervalGate("FSC-A", 0, 1)));
        assertThrows(IllegalArgumentException.class, () -> tree.addQuadrants("p1",
                List.of("a"), "FITC-A", "PI-A", 0, 0));
        assertThrows(IllegalArgumentException.class, () -> tree.moveGate("all",
                new IntervalGate("FSC-A", 0, 1)));
        assertEquals(tree, GatingTree.of(tree.root()));
        assertEquals(tree.hashCode(), GatingTree.of(tree.root()).hashCode());
        assertNotEquals(tree, renamed);
        assertNotEquals(tree, "tree");
    }

    @Test
    void movingOneQuadrantMovesItsSiblings() {
        GatingTree tree = tree();
        QuadrantGate old = (QuadrantGate) tree.find("p4").orElseThrow().gate();
        GatingTree moved = tree.moveGate("p4", old.withThresholds(550, 750));
        for (String id : List.of("p2", "p3", "p4", "p5")) {
            QuadrantGate gate = (QuadrantGate) moved.find(id).orElseThrow().gate();
            assertEquals(550, gate.xThreshold());
            assertEquals(750, gate.yThreshold());
        }
        GatingTree reshaped = tree.moveGate("p1", new IntervalGate("FSC-A", 0, 1000));
        assertEquals(6, GatingEngine.apply(sample(), reshaped).count("p1"));
        GatingTree replaced = tree.moveGate("p2", new IntervalGate("FITC-A", 0, 1));
        assertEquals(100, ((QuadrantGate) replaced.find("p3").orElseThrow().gate())
                .xThreshold());
    }

    @Test
    void rebuildsTreesAndChecksThem() {
        Population root = Population.root("All").withChild(
                Population.leaf("p7", "Cells", new IntervalGate("FSC-A", 0, 1)));
        assertEquals("p8", GatingTree.of(root).nextId());
        Population custom = Population.root("All").withChild(
                Population.leaf("cells", "Cells", new IntervalGate("FSC-A", 0, 1)));
        assertEquals("p1", GatingTree.of(custom).nextId());
        assertThrows(IllegalArgumentException.class, () -> GatingTree.of(
                Population.leaf("all", "All", new IntervalGate("FSC-A", 0, 1))));
        assertThrows(IllegalArgumentException.class, () -> GatingTree.of(
                new Population("root", "All", null, List.of())));
        assertThrows(IllegalArgumentException.class, () -> GatingTree.of(Population.root("All")
                .withChild(Population.leaf("p1", "A", new IntervalGate("FSC-A", 0, 1)))
                .withChild(Population.leaf("p1", "B", new IntervalGate("FSC-A", 0, 1)))));
        assertThrows(IllegalArgumentException.class, () -> GatingTree.of(Population.root("All")
                .withChild(new Population("p1", "A", null, List.of()))));
    }

    @Test
    void populations() {
        Population root = tree().root();
        assertEquals(6, root.flatten().size());
        assertEquals("p1", root.parentOf("p3").orElseThrow().id());
        assertEquals("all", root.parentOf("p1").orElseThrow().id());
        assertTrue(root.parentOf("all").isEmpty());
        assertTrue(root.find("p9").isEmpty());
        assertEquals(root, root.update("p9", p -> p.withName("x")));
        assertThrows(IllegalArgumentException.class, () -> Population.root(" "));
        assertThrows(IllegalArgumentException.class,
                () -> new Population(" ", "A", null, List.of()));
    }

    @Test
    void gateShapes() {
        RectangleGate rectangle = new RectangleGate("A", "B", 10, 0, 5, 1);
        assertEquals(0, rectangle.xMin());
        assertEquals(1, rectangle.yMin());
        assertTrue(rectangle.contains(5, 3));
        assertFalse(rectangle.contains(5, 6));
        assertFalse(rectangle.contains(11, 3));
        assertFalse(rectangle.contains(5, 0));
        assertFalse(rectangle.contains(-1, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new RectangleGate("A", "B", Double.NaN, 0, 0, 1));

        IntervalGate interval = new IntervalGate("A", 5, 1);
        assertEquals(1, interval.min());
        assertNull(interval.yChannel());
        assertTrue(interval.contains(3, 99));
        assertFalse(interval.contains(6, 0));
        assertFalse(interval.contains(0, 0));
        assertThrows(IllegalArgumentException.class, () -> new IntervalGate("A", 0, Double.NaN));

        PolygonGate triangle = new PolygonGate("A", "B", new double[] {0, 10, 0},
                new double[] {0, 0, 10});
        assertTrue(triangle.contains(2, 2));
        assertFalse(triangle.contains(8, 8));
        assertFalse(triangle.contains(-1, 2));
        assertEquals(3, triangle.xs().length);
        assertEquals(3, triangle.ys().length);
        assertEquals("A", triangle.xChannel());
        assertEquals("B", triangle.yChannel());
        assertEquals(triangle, new PolygonGate("A", "B", new double[] {0, 10, 0},
                new double[] {0, 0, 10}));
        assertEquals(triangle.hashCode(), new PolygonGate("A", "B", new double[] {0, 10, 0},
                new double[] {0, 0, 10}).hashCode());
        assertNotEquals(triangle, new PolygonGate("A", "C", new double[] {0, 10, 0},
                new double[] {0, 0, 10}));
        assertNotEquals(triangle, new PolygonGate("C", "B", new double[] {0, 10, 0},
                new double[] {0, 0, 10}));
        assertNotEquals(triangle, new PolygonGate("A", "B", new double[] {0, 11, 0},
                new double[] {0, 0, 10}));
        assertNotEquals(triangle, new PolygonGate("A", "B", new double[] {0, 10, 0},
                new double[] {0, 0, 11}));
        assertNotEquals(triangle, "triangle");
        assertTrue(triangle.toString().contains("3 vertices"));
        assertThrows(IllegalArgumentException.class, () -> new PolygonGate("A", "B",
                new double[] {0, 1}, new double[] {0, 1}));
        assertThrows(IllegalArgumentException.class, () -> new PolygonGate("A", "B",
                new double[] {0, 1, 2}, new double[] {0, 1}));
        assertThrows(IllegalArgumentException.class, () -> new PolygonGate("A", "B",
                new double[] {0, 1, Double.NaN}, new double[] {0, 1, 2}));
        assertThrows(IllegalArgumentException.class, () -> new PolygonGate("A", "B",
                new double[] {0, 1, 2}, new double[] {0, 1, Double.POSITIVE_INFINITY}));

        QuadrantGate quadrant = new QuadrantGate("A", "B", 10, 20, Quadrant.NEGATIVE_POSITIVE);
        assertTrue(quadrant.contains(5, 25));
        assertFalse(quadrant.contains(15, 25));
        assertFalse(quadrant.contains(5, 15));
        assertEquals("-/+", Quadrant.NEGATIVE_POSITIVE.signs());
        assertEquals("+/-", Quadrant.POSITIVE_NEGATIVE.signs());
        assertThrows(IllegalArgumentException.class,
                () -> new QuadrantGate("A", "B", Double.NaN, 0, Quadrant.POSITIVE_POSITIVE));
        assertThrows(IllegalArgumentException.class,
                () -> new QuadrantGate("A", "B", 0, Double.NaN, Quadrant.POSITIVE_POSITIVE));
    }
}
