package com.bioinfo.cytoscope.scale;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bioinfo.cytoscope.fcs.Channel;
import org.junit.jupiter.api.Test;

class ScaleTest {

    /** Values of flowutils 1.1 transforms.logicle(t=262144, w=0.5, m=4.5, a=0). */
    @Test
    void logicleLikeFlowutils() {
        double[] values = {-1000, -100, 0, 100, 1000, 10_000, 100_000, 262_144};
        double[] expected = {-0.23211535395017646, 0.009041134692025388, 0.1111111111111111,
            0.21318108753019682, 0.45433757617239867, 0.6838326572265573, 0.9069275914814589,
            1.0};
        LogicleScale scale = LogicleScale.standard(262_144);
        for (int i = 0; i < values.length; i++) {
            assertEquals(expected[i], scale.apply(values[i]), 1e-9, "value " + values[i]);
            assertEquals(values[i], scale.invert(scale.apply(values[i])),
                    1e-6 * Math.max(1, Math.abs(values[i])));
        }
    }

    /** Values of flowutils 1.1 transforms.logicle(t=10000, w=1, m=4, a=1). */
    @Test
    void logicleWithExtraNegativeDecades() {
        LogicleScale scale = new LogicleScale(10_000, 1, 4, 1);
        double[] values = {-50, 0, 50, 500, 5000};
        double[] expected = {0.31838882753731446, 0.4, 0.4816111724626856, 0.7233808845313902,
            0.9387594022846241};
        for (int i = 0; i < values.length; i++) {
            assertEquals(expected[i], scale.apply(values[i]), 1e-9);
        }
        assertEquals(1, scale.width());
        assertEquals(4, scale.decades());
        assertEquals(1, scale.extra());
        assertEquals(10_000, scale.top());
        assertEquals(ScaleType.LOGICLE, scale.type());
    }

    @Test
    void logicleParametersAndTicks() {
        LogicleScale zeroWidth = new LogicleScale(1000, 0, 3, 0);
        assertEquals(0.5, zeroWidth.apply(Math.pow(10, 1.5)), 1e-3);
        assertTrue(zeroWidth.apply(1e7) > 1);
        assertEquals(0.5, LogicleScale.forData(262_144, 10).width());
        assertEquals((4.5 - Math.log10(262_144 / 1000.0)) / 2,
                LogicleScale.forData(262_144, -1000).width(), 1e-12);
        assertEquals(2.25, LogicleScale.forData(262_144, -1e9).width());
        assertEquals(0, LogicleScale.forData(262_144, -0.001).width());
        double[] ticks = LogicleScale.standard(262_144).ticks();
        assertTrue(ticks[0] < 0);
        assertEquals(100_000, ticks[ticks.length - 1]);
        assertEquals(LogicleScale.standard(10), LogicleScale.standard(10));
        assertEquals(LogicleScale.standard(10).hashCode(), LogicleScale.standard(10).hashCode());
        assertNotEquals(LogicleScale.standard(10), LogicleScale.standard(11));
        assertNotEquals(LogicleScale.standard(10), new LogicleScale(10, 0.4, 4.5, 0));
        assertNotEquals(LogicleScale.standard(10), new LogicleScale(10, 0.5, 4, 0));
        assertNotEquals(LogicleScale.standard(10), new LogicleScale(10, 0.5, 4.5, 1));
        assertNotEquals(LogicleScale.standard(10), "scale");
        assertTrue(LogicleScale.standard(10).toString().contains("T=10.0"));
        assertThrows(IllegalArgumentException.class, () -> new LogicleScale(0, 0.5, 4.5, 0));
        assertThrows(IllegalArgumentException.class, () -> new LogicleScale(1, 0.5, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new LogicleScale(1, -1, 4.5, 0));
        assertThrows(IllegalArgumentException.class, () -> new LogicleScale(1, 0.5, 4.5, -1));
        assertThrows(IllegalArgumentException.class, () -> new LogicleScale(1, 3, 4.5, 0));
    }

    @Test
    void linearScale() {
        LinearScale scale = new LinearScale(0, 262_144);
        assertEquals(0.5, scale.apply(131_072));
        assertEquals(131_072, scale.invert(0.5));
        assertArrayEquals(new double[] {0, 50_000, 100_000, 150_000, 200_000, 250_000},
                scale.ticks());
        assertArrayEquals(new double[] {0, 5, 10, 15, 20}, new LinearScale(0, 20).ticks());
        assertEquals(ScaleType.LINEAR, scale.type());
        assertThrows(IllegalArgumentException.class, () -> new LinearScale(1, 1));
    }

    @Test
    void logScale() {
        LogScale scale = new LogScale(10, 100_000);
        assertEquals(0.5, scale.apply(1000), 1e-12);
        assertEquals(0, scale.apply(-5));
        assertEquals(1000, scale.invert(0.5), 1e-9);
        assertArrayEquals(new double[] {10, 100, 1000, 10_000, 100_000}, scale.ticks(), 1e-6);
        assertEquals(0, new LogScale(2, 5).ticks().length);
        assertEquals(ScaleType.LOG, scale.type());
        assertThrows(IllegalArgumentException.class, () -> new LogScale(0, 10));
        assertThrows(IllegalArgumentException.class, () -> new LogScale(10, 10));
    }

    @Test
    void arcsinhScale() {
        ArcsinhScale scale = new ArcsinhScale(-150, 262_144, 150);
        assertEquals(0, scale.apply(-150), 1e-12);
        assertEquals(1, scale.apply(262_144), 1e-12);
        assertEquals(1234, scale.invert(scale.apply(1234)), 1e-6);
        assertEquals(Math.log(2 + Math.sqrt(5)), ArcsinhScale.asinh(2), 1e-12);
        assertArrayEquals(new double[] {-100, -10, 0, 10, 100, 1000, 10_000, 100_000},
                scale.ticks());
        assertEquals(ScaleType.ARCSINH, scale.type());
        assertThrows(IllegalArgumentException.class, () -> new ArcsinhScale(1, 0, 150));
        assertThrows(IllegalArgumentException.class, () -> new ArcsinhScale(0, 1, 0));
    }

    @Test
    void createsTheScaleOfAChannel() {
        Channel fitc = new Channel("FITC-A", "", 262_144);
        Channel fsc = new Channel("FSC-A", "", 262_144);
        Channel width = new Channel("PI-W", "", 262_144);
        double[] values = {-500, 10, 100, 1000, 5000};
        assertEquals(ScaleType.LOGICLE, Scales.defaultType(fitc));
        assertEquals(ScaleType.LINEAR, Scales.defaultType(fsc));
        assertEquals(ScaleType.LINEAR, Scales.defaultType(width));
        assertEquals(ScaleType.LINEAR, Scales.defaultType(fitc, "fitc-a"));
        assertEquals(ScaleType.LOGICLE, Scales.defaultType(fitc, null));
        assertInstanceOf(LinearScale.class, Scales.create(ScaleType.LINEAR, fsc, values));
        assertEquals(-489.8, ((LinearScale) Scales.create(ScaleType.LINEAR, fsc, values)).min(),
                1e-9);
        assertEquals(0, ((LinearScale) Scales.create(ScaleType.LINEAR, fsc, new double[0])).min());
        assertEquals(2.62144, ((LogScale) Scales.create(ScaleType.LOG, fitc, values)).min(), 1e-9);
        ArcsinhScale asinh = (ArcsinhScale) Scales.create(ScaleType.ARCSINH, fitc, values);
        assertTrue(asinh.min() < -500);
        assertEquals(-150, ((ArcsinhScale) Scales.create(ScaleType.ARCSINH, fitc,
                new double[0])).min());
        assertTrue(((LogicleScale) Scales.create(ScaleType.LOGICLE, fitc, values)).width() > 0.5);
        assertEquals(0.5, ((LogicleScale) Scales.create(ScaleType.LOGICLE, fitc,
                new double[0])).width());
    }
}
