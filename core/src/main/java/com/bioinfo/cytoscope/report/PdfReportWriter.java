package com.bioinfo.cytoscope.report;

import com.bioinfo.cytoscope.analysis.ApoptosisSettings;
import com.bioinfo.cytoscope.analysis.CellCyclePhase;
import com.bioinfo.cytoscope.analysis.CellCycleResult;
import com.bioinfo.cytoscope.analysis.CellFate;
import com.bioinfo.cytoscope.compensation.SpilloverMatrix;
import com.bioinfo.cytoscope.experiment.DoseResponse;
import com.bioinfo.cytoscope.experiment.Experiment;
import com.bioinfo.cytoscope.experiment.ExperimentResults;
import com.bioinfo.cytoscope.experiment.GroupComparison;
import com.bioinfo.cytoscope.experiment.GroupComparison.GroupSummary;
import com.bioinfo.cytoscope.experiment.PopulationStats;
import com.bioinfo.cytoscope.experiment.SampleInfo;
import com.bioinfo.cytoscope.experiment.SampleResult;
import com.bioinfo.cytoscope.gating.Gate;
import com.bioinfo.cytoscope.gating.IntervalGate;
import com.bioinfo.cytoscope.gating.PolygonGate;
import com.bioinfo.cytoscope.gating.Population;
import com.bioinfo.cytoscope.gating.QuadrantGate;
import com.bioinfo.cytoscope.gating.RectangleGate;
import com.bioinfo.cytoscope.plot.Charts;
import com.bioinfo.cytoscope.report.PdfCanvas.TableRow;
import com.bioinfo.cytoscope.stats.FourParameterLogistic;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

/**
 * Renders a {@link CytoReport} as a PDF document: set-up of the experiment, figures, results of
 * each analysis, comparison of the groups, dose-response curve and notes. Every page carries the
 * signature of the author, its number, a disclaimer and the provenance.
 *
 * <p>Instances are stateless and thread-safe.
 */
public final class PdfReportWriter {

    /** Name of the application, in the title and the footer. */
    public static final String APP_NAME = "CytoScope";

    /** Contact e-mail of the author, in the footer. */
    public static final String CONTACT_EMAIL = "contact@maximeethier.com";

    private static final float LABEL_WIDTH = 120;

    /**
     * Renders a report in memory, such as for an HTTP response.
     *
     * @param report the report
     * @return the PDF file
     * @throws IOException if the document cannot be produced
     */
    public byte[] toBytes(CytoReport report) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        write(report, bytes);
        return bytes.toByteArray();
    }

    /**
     * Renders a report.
     *
     * @param report the report
     * @param output receives the PDF file; it is not closed
     * @throws IOException if the document cannot be produced or written
     */
    public void write(CytoReport report, OutputStream output) throws IOException {
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(output, "output");
        ReportTexts texts = new ReportTexts(report.locale(), report.zone());
        try (PDDocument document = new PDDocument()) {
            metadata(document, report, texts);
            PdfCanvas canvas = new PdfCanvas(document, pageSizeFor(report.locale()));
            canvas.newPage();
            try {
                new Sections(canvas, texts, report).render();
            } finally {
                canvas.finish();
            }
            String provenance = texts.get("footer.generated",
                    texts.dateTime(report.generatedAt()), APP_NAME, report.appVersion());
            canvas.footers(texts, provenance, CONTACT_EMAIL);
            document.save(output);
        }
    }

    /**
     * Returns the usual paper size of a locale: US Letter in the United States and Canada, A4
     * elsewhere.
     *
     * @param locale locale of the reader
     * @return the page size
     */
    public static PDRectangle pageSizeFor(Locale locale) {
        String country = locale.getCountry();
        return "US".equals(country) || "CA".equals(country) ? PDRectangle.LETTER : PDRectangle.A4;
    }

    private static void metadata(PDDocument document, CytoReport report, ReportTexts texts) {
        PDDocumentInformation info = document.getDocumentInformation();
        info.setTitle(report.title() + " - " + texts.get("report.title"));
        info.setAuthor(report.analyst().orElse(texts.get("brand.title")));
        info.setCreator(APP_NAME + " " + report.appVersion());
        info.setKeywords(texts.get("brand.website") + ", " + CONTACT_EMAIL);
        Calendar created = GregorianCalendar.getInstance();
        created.setTimeInMillis(report.generatedAt().toEpochMilli());
        info.setCreationDate(created);
    }

    /** Writes the sections of one report. */
    private static final class Sections {

        private final PdfCanvas canvas;
        private final ReportTexts texts;
        private final CytoReport report;
        private final Experiment experiment;
        private final ExperimentResults results;

        Sections(PdfCanvas canvas, ReportTexts texts, CytoReport report) {
            this.canvas = canvas;
            this.texts = texts;
            this.report = report;
            this.experiment = report.experiment();
            this.results = report.results();
        }

        void render() throws IOException {
            header();
            tubes();
            compensation();
            gating();
            if (!report.figures().isEmpty()) {
                canvas.sectionTitle(texts.get("section.figures"));
                canvas.figures(report.figures());
            }
            apoptosis();
            cellCycle();
            comparison();
            doseResponse();
            if (report.notes().isPresent()) {
                canvas.sectionTitle(texts.get("section.notes"));
                for (String paragraph : report.notes().get().split("\\R")) {
                    canvas.paragraph(paragraph, canvas.regular, PdfCanvas.BODY_SIZE,
                            PdfCanvas.TEXT);
                }
            }
        }

        private void header() throws IOException {
            canvas.paragraph(APP_NAME + "  ·  " + texts.get("report.title"), canvas.bold,
                    PdfCanvas.SMALL_SIZE + 1, PdfCanvas.ACCENT);
            canvas.gap(8);
            canvas.title(report.title(), 20);
            canvas.gap(6);
            canvas.detailRow(texts.get("header.tubes"), texts.get("header.tubeCount",
                    experiment.samples().size(), experiment.groups().size()), LABEL_WIDTH);
            canvas.detailRow(texts.get("header.compensation"),
                    texts.of("compensation", experiment.compensation()), LABEL_WIDTH);
            if (report.analyst().isPresent()) {
                canvas.detailRow(texts.get("header.analyst"), report.analyst().get(),
                        LABEL_WIDTH);
            }
            canvas.detailRow(texts.get("header.generated"),
                    texts.dateTime(report.generatedAt()), LABEL_WIDTH);
            if (report.source().isPresent()) {
                canvas.detailRow(texts.get("header.source"), report.source().get(),
                        LABEL_WIDTH);
            }
        }

        private void tubes() throws IOException {
            canvas.sectionTitle(texts.get("section.tubes"));
            List<TableRow> rows = new ArrayList<>();
            for (SampleResult sample : results.samples()) {
                SampleInfo info = sample.info();
                String role = texts.of("role", info.role());
                if (!info.stainChannel().isEmpty()) {
                    role += " (" + info.stainChannel() + ")";
                }
                rows.add(TableRow.of(List.of(info.name(), role, info.group(),
                        Double.isNaN(info.dose()) ? "" : Charts.plain(info.dose()),
                        String.format(texts.locale(), "%,d", sample.eventCount()))));
            }
            canvas.table(List.of(texts.get("table.tube"), texts.get("table.role"),
                    texts.get("table.group"), texts.get("table.dose"), texts.get("table.events")),
                    new float[] {0.34f, 0.24f, 0.18f, 0.1f, 0.14f}, rows);
        }

        private void compensation() throws IOException {
            SpilloverMatrix matrix = experiment.matrix();
            if (matrix == null) {
                return;
            }
            canvas.sectionTitle(texts.get("section.compensation"));
            caption(texts.get("compensation.caption"));
            List<String> channels = matrix.channels();
            List<String> headers = new ArrayList<>();
            headers.add(texts.get("table.fluorochrome"));
            headers.addAll(channels);
            List<TableRow> rows = new ArrayList<>();
            for (int i = 0; i < channels.size(); i++) {
                List<String> cells = new ArrayList<>();
                cells.add(channels.get(i));
                for (int j = 0; j < channels.size(); j++) {
                    cells.add(texts.percent(100 * matrix.value(i, j)));
                }
                rows.add(TableRow.of(cells));
            }
            canvas.table(headers, equalWidths(headers.size()), rows);
        }

        private void gating() throws IOException {
            canvas.sectionTitle(texts.get("section.gating"));
            caption(texts.get("gating.caption"));
            List<Population> populations = experiment.gating().root().flatten();
            List<TableRow> rows = new ArrayList<>();
            for (Population population : populations) {
                if (population.gate() == null) {
                    continue;
                }
                String parent = experiment.gating().root().parentOf(population.id())
                        .map(Population::name).orElse("");
                double[] percents = results.samples().stream()
                        .filter(s -> s.info().isCompared())
                        .flatMap(s -> s.populations().stream())
                        .filter(p -> p.populationId().equals(population.id()))
                        .mapToDouble(PopulationStats::percentOfParent).toArray();
                String range = percents.length == 0 ? "–"
                        : texts.get("gating.range",
                                texts.percent(Arrays.stream(percents).min().orElse(0)),
                                texts.percent(Arrays.stream(percents).max().orElse(0)));
                rows.add(TableRow.of(List.of(population.name(), parent,
                        describe(population.gate()), range)));
            }
            canvas.table(List.of(texts.get("table.population"), texts.get("table.parent"),
                    texts.get("table.gate"), texts.get("table.percentParent")),
                    new float[] {0.22f, 0.2f, 0.36f, 0.22f}, rows);
            ApoptosisSettings apoptosis = experiment.apoptosis();
            if (apoptosis != null) {
                caption(texts.get("gating.apoptosis", populationName(apoptosis.parentId()),
                        apoptosis.annexinChannel(), texts.number(apoptosis.annexinThreshold(), 0),
                        apoptosis.piChannel(), texts.number(apoptosis.piThreshold(), 0)));
            }
            if (experiment.cellCycle() != null) {
                caption(texts.get("gating.cellCycle",
                        populationName(experiment.cellCycle().parentId()),
                        experiment.cellCycle().dnaChannel()));
            }
        }

        private String describe(Gate gate) {
            return switch (gate) {
                case RectangleGate r -> texts.get("gate.rectangle", r.xChannel(), r.yChannel(),
                        Charts.plain(Math.round(r.xMin())), Charts.plain(Math.round(r.xMax())),
                        Charts.plain(Math.round(r.yMin())), Charts.plain(Math.round(r.yMax())));
                case PolygonGate p -> texts.get("gate.polygon", p.xChannel(), p.yChannel(),
                        p.xs().length);
                case QuadrantGate q -> texts.get("gate.quadrant", q.xChannel(), q.yChannel(),
                        q.quadrant().signs(), texts.number(q.xThreshold(), 0),
                        texts.number(q.yThreshold(), 0));
                case IntervalGate i -> texts.get("gate.interval", i.xChannel(),
                        Charts.plain(Math.round(i.min())), Charts.plain(Math.round(i.max())));
            };
        }

        private String populationName(String id) {
            return experiment.gating().find(id).map(Population::name).orElse(id);
        }

        private void apoptosis() throws IOException {
            List<SampleResult> rowsWith = results.samples().stream()
                    .filter(s -> s.apoptosis() != null).toList();
            if (rowsWith.isEmpty()) {
                return;
            }
            canvas.sectionTitle(texts.get("section.apoptosis"));
            caption(texts.get("apoptosis.caption"));
            List<TableRow> rows = new ArrayList<>();
            for (SampleResult s : rowsWith) {
                List<String> cells = new ArrayList<>(List.of(s.info().name(), s.info().group()));
                for (CellFate fate : CellFate.values()) {
                    cells.add(texts.percent(s.apoptosis().percent(fate)));
                }
                rows.add(TableRow.of(cells));
            }
            List<String> headers = new ArrayList<>(List.of(texts.get("table.tube"),
                    texts.get("table.group")));
            for (CellFate fate : CellFate.values()) {
                headers.add(texts.get("fate." + fate.name()));
            }
            canvas.table(headers, new float[] {0.28f, 0.16f, 0.14f, 0.14f, 0.14f, 0.14f}, rows);
        }

        private void cellCycle() throws IOException {
            List<SampleResult> rowsWith = results.samples().stream()
                    .filter(s -> s.cellCycle() != null || s.cellCycleIssue() != null).toList();
            if (rowsWith.isEmpty()) {
                return;
            }
            canvas.sectionTitle(texts.get("section.cellCycle"));
            caption(texts.get("cellCycle.caption"));
            List<TableRow> rows = new ArrayList<>();
            for (SampleResult s : rowsWith) {
                List<String> cells = new ArrayList<>(List.of(s.info().name(), s.info().group()));
                CellCycleResult r = s.cellCycle();
                if (r == null) {
                    cells.add(texts.of("issue", s.cellCycleIssue()));
                    for (int i = 0; i < 5; i++) {
                        cells.add("");
                    }
                } else {
                    cells.add(Charts.plain(Math.round(r.g1Peak() / 100.0) * 100.0));
                    cells.add(texts.get("format.percent", texts.number(r.g1Cv(), 1)));
                    for (CellCyclePhase phase : List.of(CellCyclePhase.SUB_G1,
                            CellCyclePhase.G0_G1, CellCyclePhase.S, CellCyclePhase.G2_M)) {
                        cells.add(texts.percent(r.percent(phase)));
                    }
                }
                rows.add(TableRow.of(cells));
            }
            List<String> headers = new ArrayList<>(List.of(texts.get("table.tube"),
                    texts.get("table.group"), texts.get("table.g1Peak"), texts.get("table.cv")));
            for (CellCyclePhase phase : List.of(CellCyclePhase.SUB_G1, CellCyclePhase.G0_G1,
                    CellCyclePhase.S, CellCyclePhase.G2_M)) {
                headers.add(texts.get("phase." + phase.name()));
            }
            canvas.table(headers,
                    new float[] {0.22f, 0.12f, 0.1f, 0.08f, 0.12f, 0.12f, 0.12f, 0.12f}, rows);
        }

        private void comparison() throws IOException {
            if (experiment.metric() == null) {
                return;
            }
            canvas.sectionTitle(texts.get("section.comparison"));
            String metric = texts.metric(experiment.metric(), experiment.gating());
            GroupComparison comparison = results.comparison();
            if (comparison == null) {
                caption(texts.get("comparison.unavailable", metric,
                        texts.of("issue", results.comparisonIssue())));
                return;
            }
            caption(texts.get("comparison.caption", metric, comparison.controlGroup(),
                    texts.number(comparison.anova().f(), 2), comparison.anova().groupCount() - 1,
                    comparison.anova().totalCount() - comparison.anova().groupCount(),
                    texts.pRelation(comparison.anova().pValue())));
            List<TableRow> rows = new ArrayList<>();
            for (GroupSummary g : comparison.groups()) {
                boolean control = g.comparison() == null;
                rows.add(new TableRow(List.of(g.name(), String.valueOf(g.values().length),
                        texts.number(g.mean(), 2) + " ± " + texts.number(g.standardDeviation(), 2),
                        control ? texts.get("comparison.control")
                                : texts.number(g.comparison().difference(), 2),
                        control ? "" : texts.pValue(g.comparison().adjustedP()),
                        control ? "" : g.stars().isEmpty() ? "ns" : g.stars()),
                        0, control ? PdfCanvas.MUTED
                                : g.comparison().adjustedP() < 0.05 ? PdfCanvas.HIGH
                                : PdfCanvas.LOW));
            }
            canvas.table(List.of(texts.get("table.group"), "n", texts.get("table.meanSd"),
                    texts.get("table.difference"), texts.get("table.pAdjusted"), ""),
                    new float[] {0.24f, 0.06f, 0.22f, 0.2f, 0.18f, 0.1f}, rows);
        }

        private void doseResponse() throws IOException {
            if (experiment.metric() == null || (results.doseResponse() == null
                    && experiment.samples().stream().noneMatch(s -> !Double.isNaN(s.dose())))) {
                return;
            }
            canvas.sectionTitle(texts.get("section.doseResponse"));
            DoseResponse curve = results.doseResponse();
            if (curve == null) {
                caption(texts.get("doseResponse.unavailable",
                        texts.of("issue", results.doseResponseIssue())));
                return;
            }
            FourParameterLogistic fit = curve.fit();
            caption(texts.get("doseResponse.caption"));
            List<TableRow> rows = List.of(
                    TableRow.of(List.of(texts.get("fit.ec50"), texts.number(fit.ec50(), 3)
                            + "  [" + texts.number(fit.ec50Low(), 3) + " – "
                            + texts.number(fit.ec50High(), 3) + "]")),
                    TableRow.of(List.of(texts.get("fit.bottom"), texts.number(fit.bottom(), 2))),
                    TableRow.of(List.of(texts.get("fit.top"), texts.number(fit.top(), 2))),
                    TableRow.of(List.of(texts.get("fit.hill"), texts.number(fit.hill(), 2))),
                    TableRow.of(List.of("R²", texts.number(fit.rSquared(), 3))),
                    TableRow.of(List.of(texts.get("fit.points"),
                            String.valueOf(fit.pointCount()))));
            canvas.table(List.of(texts.get("table.parameter"), texts.get("table.value")),
                    new float[] {0.4f, 0.6f}, rows);
        }

        private void caption(String text) throws IOException {
            canvas.paragraph(text, canvas.regular, PdfCanvas.SMALL_SIZE + 0.5f, PdfCanvas.MUTED);
            canvas.gap(4);
        }

        private static float[] equalWidths(int count) {
            float[] widths = new float[count];
            Arrays.fill(widths, 1f / count);
            return widths;
        }
    }
}
