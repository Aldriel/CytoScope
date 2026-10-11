package com.bioinfo.cytoscope.plot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bioinfo.cytoscope.experiment.DoseResponse;
import com.bioinfo.cytoscope.experiment.GroupComparison;
import com.bioinfo.cytoscope.gating.IntervalGate;
import com.bioinfo.cytoscope.gating.PolygonGate;
import com.bioinfo.cytoscope.gating.RectangleGate;
import com.bioinfo.cytoscope.scale.LinearScale;
import com.bioinfo.cytoscope.scale.LogicleScale;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ChartsTest {

    private static void assertDrawn(BufferedImage image) {
        assertEquals(2 * Charts.WIDTH, image.getWidth());
        assertEquals(2 * Charts.HEIGHT, image.getHeight());
        int colored = 0;
        for (int x = 0; x < image.getWidth(); x += 3) {
            for (int y = 0; y < image.getHeight(); y += 3) {
                if ((image.getRGB(x, y) & 0xffffff) != 0xffffff) {
                    colored++;
                }
            }
        }
        assertTrue(colored > 500, "Too few pixels drawn: " + colored);
    }

    @Test
    void formatsTicks() {
        assertEquals("0", Charts.tick(0));
        assertEquals("10³", Charts.tick(1000));
        assertEquals("-10²", Charts.tick(-100));
        assertEquals("0.01", Charts.tick(0.01));
        assertEquals("50K", Charts.tick(50_000));
        assertEquals("2.5", Charts.tick(2.5));
        assertEquals("10", Charts.tick(10));
        assertEquals("1.5K", Charts.plain(1500));
        assertEquals("-20", Charts.plain(-20));
        assertEquals("262.14K", Charts.plain(262_144));
    }

    @Test
    void heatGoesFromBlueToRed() {
        assertEquals(new Color(0.12f, 0.25f, 0.85f), Charts.heat(-1));
        assertEquals(new Color(0.9f, 0.15f, 0.1f), Charts.heat(2));
        assertTrue(Charts.heat(0.5).getGreen() > Charts.heat(0.5).getBlue());
    }

    @Test
    void drawsDensityPlotsWithGatesAndQuadrants() {
        Random random = new Random(7);
        double[] x = new double[5_000];
        double[] y = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            x[i] = 50_000 + random.nextGaussian() * 20_000;
            y[i] = 2_000 + random.nextGaussian() * 3_000;
        }
        LinearScale linear = new LinearScale(0, 262_144);
        LogicleScale logicle = new LogicleScale(262_144, 0.5, 4.5, 0);
        BufferedImage gated = Charts.dotPlot(new Charts.DotPlot("A very long title that will "
                + "not fit in the width of the picture at all", x, y, linear, logicle, "FSC-A",
                "PI-A", List.of(new RectangleGate("FSC-A", "PI-A", 10_000, 90_000, 0, 9_000),
                        new IntervalGate("FSC-A", 20_000, 80_000),
                        new PolygonGate("FSC-A", "PI-A", new double[] {1, 9e4, 9e4},
                                new double[] {1, 1, 9e3})), null, List.of()));
        assertDrawn(gated);
        BufferedImage quadrants = Charts.dotPlot(new Charts.DotPlot("Q", x, y, logicle, logicle,
                "FITC-A", "PI-A", List.of(), new double[] {1_000, 1_000},
                List.of("80%", "10%", "5%", "5%")));
        assertDrawn(quadrants);
    }

    @Test
    void drawsHistogramsBarsAndCurves() {
        Random random = new Random(3);
        double[] dna = new double[4_000];
        for (int i = 0; i < dna.length; i++) {
            dna[i] = (i % 3 == 0 ? 100_000 : 50_000) + random.nextGaussian() * 2_000;
        }
        assertDrawn(Charts.histogram("DNA", dna, new LinearScale(0, 262_144), "PI-A",
                new double[] {45_000, 55_000, 95_000, 105_000}));

        Map<String, double[]> groups = new LinkedHashMap<>();
        groups.put("NC", new double[] {4, 5, 4.5});
        groups.put("TMZ with a long name", new double[] {20, 22, 21});
        groups.put("AZI", new double[] {5, 4, 6});
        assertDrawn(Charts.barChart("Apoptosis", GroupComparison.of(groups, "NC"), "%"));
        Map<String, double[]> single = new LinkedHashMap<>();
        single.put("NC", new double[] {0});
        single.put("T", new double[] {0, 0});
        assertDrawn(Charts.barChart("Zero", GroupComparison.of(single, "NC"), "%"));

        List<DoseResponse.Point> points = new ArrayList<>();
        for (double dose : new double[] {0, 0.1, 1, 10, 100, 1000}) {
            points.add(new DoseResponse.Point("d" + dose, dose,
                    10 + 60 / (1 + Math.pow(10 / Math.max(dose, 1e-9), 1.2))));
        }
        assertDrawn(Charts.doseResponse("Curve", DoseResponse.of(points), "µM", "%"));
        assertDrawn(Charts.doseResponse("No zero", DoseResponse.of(points.subList(1, 6)), "µM",
                "%"));
        List<DoseResponse.Point> flat = new ArrayList<>();
        for (double dose : new double[] {1, 2, 3, 4, 5}) {
            flat.add(new DoseResponse.Point("f", dose, 1 + dose * 1e-3));
        }
        assertDrawn(Charts.doseResponse("Flat", DoseResponse.of(flat), "µM", "%"));
    }
}
