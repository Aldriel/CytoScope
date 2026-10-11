package com.bioinfo.cytoscope.tools;

import com.bioinfo.cytoscope.experiment.SampleAnnotator;
import com.bioinfo.cytoscope.fcs.Channel;
import com.bioinfo.cytoscope.fcs.FcsSample;
import com.bioinfo.cytoscope.fcs.FcsWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Writes the simulated example series: mouse splenocytes exposed for 24 h to cadmium chloride,
 * stained with annexin V-FITC and propidium iodide.
 *
 * <p>The data are invented, to illustrate a dose-response curve; the files say so in their
 * keywords. The share of apoptotic cells follows a logistic curve (8 % to 70 %, EC50 = 8 µM,
 * Hill slope 1.6) with a replicate scatter of 2 points. The dyes spill into each other's
 * detector (FITC into PI 12 %, PI into FITC 2 %), and the files carry this matrix.
 *
 * <p>Run with {@code mvnw -pl core exec:java -Dexec.args=<output directory>}.
 */
public final class SimulatedDoseSeries {

    static final double[] DOSES = {0, 1, 2.5, 5, 10, 25, 50};
    static final int REPLICATES = 3;
    private static final int EVENTS = 10_000;
    private static final double RANGE = 262_144;
    private static final double FITC_INTO_PI = 0.12;
    private static final double PI_INTO_FITC = 0.02;

    private SimulatedDoseSeries() {}

    /**
     * Writes the files.
     *
     * @param args the output directory
     * @throws IOException if a file cannot be written
     */
    public static void main(String[] args) throws IOException {
        Path directory = Path.of(args.length > 0 ? args[0]
                : "core/src/main/resources/com/bioinfo/cytoscope/examples/cadmium");
        Files.createDirectories(directory);
        long seed = 20_261_011;
        for (double dose : DOSES) {
            for (int replicate = 1; replicate <= REPLICATES; replicate++) {
                Random random = new Random(seed++);
                double apoptotic = apoptoticShare(dose) + random.nextGaussian() * 0.02;
                String name = "Cd_" + formatDose(dose) + "uM_" + replicate;
                Map<String, String> keywords = keywords();
                keywords.put(SampleAnnotator.GROUP_KEYWORD, formatDose(dose) + " µM");
                keywords.put(SampleAnnotator.DOSE_KEYWORD, formatDose(dose));
                write(directory, name, keywords,
                        simulate(random, Math.max(0.01, Math.min(0.95, apoptotic)), true, true));
            }
        }
        write(directory, "Control_unstained", role(keywords(), "UNSTAINED"),
                simulate(new Random(seed++), apoptoticShare(0), false, false));
        write(directory, "Control_annexin_only", role(keywords(), "SINGLE_STAIN:FITC-A"),
                simulate(new Random(seed++), 0.35, true, false));
        write(directory, "Control_PI_only", role(keywords(), "SINGLE_STAIN:PI-A"),
                simulate(new Random(seed), 0.35, false, true));
    }

    /** Share of apoptotic cells at a dose: logistic from 8 % to 70 %, EC50 8 µM, slope 1.6. */
    static double apoptoticShare(double dose) {
        if (dose <= 0) {
            return 0.08;
        }
        return 0.08 + (0.70 - 0.08) / (1 + Math.pow(8 / dose, 1.6));
    }

    private static String formatDose(double dose) {
        return dose == Math.rint(dose) ? Integer.toString((int) dose)
                : String.format(Locale.ROOT, "%.1f", dose);
    }

    private static Map<String, String> keywords() {
        Map<String, String> keywords = new LinkedHashMap<>();
        keywords.put("$CYT", "Simulated by CytoScope");
        keywords.put("$SRC", "Simulated mouse splenocytes, CdCl2 24 h (not real data)");
        keywords.put("$DATE", "11-OCT-2026");
        keywords.put("$SPILLOVER", String.format(Locale.ROOT, "2,FITC-A,PI-A,1,%s,%s,1",
                FITC_INTO_PI, PI_INTO_FITC));
        return keywords;
    }

    private static Map<String, String> role(Map<String, String> keywords, String role) {
        keywords.put(SampleAnnotator.ROLE_KEYWORD, role);
        return keywords;
    }

    /**
     * Simulates one tube. 8 % of the events are debris; the cells are live, early apoptotic
     * (annexin V+), late apoptotic (annexin V+ PI+) or necrotic (PI+).
     */
    private static float[][] simulate(Random random, double apoptotic, boolean annexin,
                                      boolean pi) {
        double necrotic = 0.02 + 0.08 * apoptotic;
        double earlyShare = 0.62 - 0.3 * apoptotic;
        float[][] values = new float[5][EVENTS];
        for (int e = 0; e < EVENTS; e++) {
            double fsc;
            double ssc;
            double annexinSignal = autofluorescence(random, 110);
            double piSignal = autofluorescence(random, 90);
            if (random.nextDouble() < 0.08) {
                fsc = Math.abs(random.nextGaussian() * 6_000 + 9_000);
                ssc = Math.abs(random.nextGaussian() * 5_000 + 7_000);
                annexinSignal += annexin ? logNormal(random, 600, 1.2) : 0;
                piSignal += pi ? logNormal(random, 900, 1.2) : 0;
            } else {
                double u = random.nextDouble();
                boolean early = u < apoptotic * earlyShare;
                boolean late = !early && u < apoptotic;
                boolean dead = !early && !late && u < apoptotic + necrotic;
                double shrink = early ? 0.85 : late || dead ? 0.7 : 1;
                fsc = (random.nextGaussian() * 9_000 + 62_000) * shrink;
                ssc = (random.nextGaussian() * 5_000 + 24_000) * (late || dead ? 1.25 : 1);
                if (annexin && (early || late)) {
                    annexinSignal += logNormal(random, late ? 14_000 : 7_000, 0.45);
                }
                if (pi && (late || dead)) {
                    piSignal += logNormal(random, 18_000, 0.4);
                }
            }
            values[0][e] = (float) clamp(fsc);
            values[1][e] = (float) clamp(ssc);
            values[2][e] = (float) clamp(annexinSignal + PI_INTO_FITC * piSignal
                    + random.nextGaussian() * 35);
            values[3][e] = (float) clamp(piSignal + FITC_INTO_PI * annexinSignal
                    + random.nextGaussian() * 35);
            values[4][e] = (float) (e * 0.05);
        }
        return values;
    }

    private static double autofluorescence(Random random, double median) {
        return logNormal(random, median, 0.35);
    }

    private static double logNormal(Random random, double median, double sigma) {
        return median * Math.exp(random.nextGaussian() * sigma);
    }

    private static double clamp(double value) {
        return Math.max(-RANGE, Math.min(RANGE - 1, value));
    }

    private static void write(Path directory, String name, Map<String, String> keywords,
                              float[][] values) throws IOException {
        List<Channel> channels = List.of(
                new Channel("FSC-A", "", RANGE),
                new Channel("SSC-A", "", RANGE),
                new Channel("FITC-A", "Annexin V", RANGE),
                new Channel("PI-A", "Propidium iodide", RANGE),
                new Channel("Time", "", RANGE));
        FcsSample sample = new FcsSample(name, keywords, channels, values);
        Files.write(directory.resolve(name + ".fcs"), FcsWriter.write(sample));
    }
}
