package com.bioinfo.cytoscope.tools;

import com.bioinfo.cytoscope.analysis.CellCyclePhase;
import com.bioinfo.cytoscope.analysis.CellFate;
import com.bioinfo.cytoscope.experiment.ExperimentResults;
import com.bioinfo.cytoscope.experiment.GroupComparison;
import com.bioinfo.cytoscope.experiment.SampleResult;
import com.bioinfo.cytoscope.service.CytoService;
import com.bioinfo.cytoscope.service.Example;
import com.bioinfo.cytoscope.service.ExampleLibrary;
import com.bioinfo.cytoscope.service.InMemorySampleStore;
import com.bioinfo.cytoscope.report.CytoReport;
import com.bioinfo.cytoscope.report.PdfReportWriter;
import com.bioinfo.cytoscope.report.ReportFigures;
import com.bioinfo.cytoscope.report.ReportTexts;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/** Prints the results of the examples, to check the presets while developing. */
public final class ExampleSummary {

    private ExampleSummary() {}

    /**
     * Prints the results.
     *
     * @param args not used
     */
    public static void main(String[] args) throws IOException {
        Files.createDirectories(Path.of("target"));
        CytoService service = new CytoService();
        AtomicInteger next = new AtomicInteger();
        for (Example example : Example.values()) {
            ExampleLibrary.OpenedExample opened = new ExampleLibrary(service).open(example, "All",
                    ExampleLibrary.PresetNames.ENGLISH, () -> "s" + next.incrementAndGet());
            InMemorySampleStore store = new InMemorySampleStore();
            store.putAll(opened.samples());
            ExperimentResults results = service.analyze(store, opened.experiment());
            System.out.println("== " + example + " " + opened.experiment().compensation() + " "
                    + (opened.experiment().matrix() == null ? ""
                    : opened.experiment().matrix().format()));
            System.out.println("   apoptosis " + opened.experiment().apoptosis());
            for (SampleResult sample : results.samples()) {
                StringBuilder line = new StringBuilder(String.format(Locale.ROOT,
                        "%-28s %-12s %-9s",
                        sample.info().name(), sample.info().role(), sample.info().group()));
                sample.populations().forEach(p -> line.append(String.format(Locale.ROOT,
                        " %s=%.1f%%", p.populationId(), p.percentOfParent())));
                if (sample.apoptosis() != null) {
                    for (CellFate fate : CellFate.values()) {
                        line.append(String.format(Locale.ROOT, " %s=%.1f", fate,
                                sample.apoptosis().percent(fate)));
                    }
                }
                if (sample.cellCycle() != null) {
                    line.append(String.format(Locale.ROOT, " G1=%.0f CV=%.1f",
                            sample.cellCycle().g1Peak(), sample.cellCycle().g1Cv()));
                    for (CellCyclePhase phase : CellCyclePhase.values()) {
                        line.append(String.format(Locale.ROOT, " %s=%.1f", phase,
                                sample.cellCycle().percent(phase)));
                    }
                }
                if (sample.cellCycleIssue() != null) {
                    line.append(" ").append(sample.cellCycleIssue());
                }
                System.out.println(line);
            }
            if (results.comparison() != null) {
                GroupComparison comparison = results.comparison();
                System.out.printf(Locale.ROOT, "   ANOVA F=%.2f p=%.2g%n", comparison.anova().f(),
                        comparison.anova().pValue());
                comparison.groups().forEach(g -> System.out.printf(Locale.ROOT,
                        "   %-10s %.2f ± %.2f %s %s%n", g.name(), g.mean(), g.standardDeviation(),
                        g.comparison() == null ? "" : String.format(Locale.ROOT, "p=%.3g",
                                g.comparison().adjustedP()), g.stars()));
            } else {
                System.out.println("   comparison: " + results.comparisonIssue());
            }
            if (results.doseResponse() != null) {
                System.out.println("   4PL " + results.doseResponse().fit());
            } else {
                System.out.println("   dose-response: " + results.doseResponseIssue());
            }
            for (Locale locale : List.of(Locale.ENGLISH, Locale.CANADA_FRENCH)) {
                ReportTexts texts = new ReportTexts(locale, ZoneId.systemDefault());
                CytoReport report = CytoReport.builder(opened.experiment(), results)
                        .title(example.folder()).locale(locale, ZoneId.systemDefault())
                        .source(example.simulated() ? "Simulated" : "Mendeley Data")
                        .figures(ReportFigures.of(opened.experiment(), results,
                                id -> service.gate(store, opened.experiment(), id), texts))
                        .build();
                Path file = Path.of("target", example.folder() + "-" + locale + ".pdf");
                Files.write(file, new PdfReportWriter().toBytes(report));
                System.out.println("   wrote " + file.toAbsolutePath());
            }
        }
    }
}
