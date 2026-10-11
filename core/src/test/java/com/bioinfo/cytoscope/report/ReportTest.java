package com.bioinfo.cytoscope.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bioinfo.cytoscope.analysis.CellCyclePhase;
import com.bioinfo.cytoscope.analysis.CellCycleSettings;
import com.bioinfo.cytoscope.analysis.CellFate;
import com.bioinfo.cytoscope.compensation.SpilloverMatrix;
import com.bioinfo.cytoscope.experiment.AnalysisIssue;
import com.bioinfo.cytoscope.experiment.CompensationSource;
import com.bioinfo.cytoscope.experiment.Experiment;
import com.bioinfo.cytoscope.experiment.ExperimentResults;
import com.bioinfo.cytoscope.experiment.Metric;
import com.bioinfo.cytoscope.experiment.SampleInfo;
import com.bioinfo.cytoscope.experiment.SampleRole;
import com.bioinfo.cytoscope.experiment.Tubes;
import com.bioinfo.cytoscope.fcs.FcsSample;
import com.bioinfo.cytoscope.gating.GatingTree;
import com.bioinfo.cytoscope.gating.PolygonGate;
import com.bioinfo.cytoscope.gating.RectangleGate;
import com.bioinfo.cytoscope.service.CytoService;
import com.bioinfo.cytoscope.service.Example;
import com.bioinfo.cytoscope.service.ExampleLibrary;
import com.bioinfo.cytoscope.service.InMemorySampleStore;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ReportTest {

    private static final Locale CANADIAN_FRENCH = Locale.forLanguageTag("fr-CA");
    private static final ZoneId MONTREAL = ZoneId.of("America/Montreal");
    private static final Instant WHEN = Instant.parse("2026-10-11T14:30:00Z");

    private final CytoService service = new CytoService();

    /** Text of a PDF, with the line breaks turned into spaces. */
    private static String text(byte[] pdf, int[] pages) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            pages[0] = document.getNumberOfPages();
            return new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
        }
    }

    private static String flat(String value) {
        return value.replaceAll("\\s+", " ");
    }

    @ParameterizedTest
    @CsvSource({"CADMIUM, en", "U87_APOPTOSIS, fr-CA", "U87_CELL_CYCLE, en-US"})
    void rendersTheExamples(Example example, String language) throws IOException {
        Locale locale = Locale.forLanguageTag(language);
        AtomicInteger next = new AtomicInteger();
        ExampleLibrary.OpenedExample opened = new ExampleLibrary(service).open(example, "All",
                ExampleLibrary.PresetNames.ENGLISH, () -> "s" + next.incrementAndGet());
        InMemorySampleStore store = new InMemorySampleStore();
        store.putAll(opened.samples());
        Experiment experiment = opened.experiment();
        ExperimentResults results = service.analyze(store, experiment);
        ReportTexts texts = new ReportTexts(locale, MONTREAL);
        List<Figure> figures = ReportFigures.of(experiment, results,
                id -> service.gate(store, experiment, id), texts);
        assertTrue(figures.size() >= 4);

        CytoReport report = CytoReport.builder(experiment, results).title(example.folder())
                .analyst("Maxime Ethier").notes("First line\nSecond line")
                .source("Example data").locale(locale, MONTREAL).generatedAt(WHEN)
                .appVersion("1.0.0").figures(figures).build();
        byte[] pdf = new PdfReportWriter().toBytes(report);
        int[] pages = new int[1];
        String text = text(pdf, pages);
        assertTrue(pages[0] >= 3, "pages: " + pages[0]);
        assertTrue(text.contains(example.folder()));
        assertTrue(text.contains("Maxime Ethier"));
        assertTrue(text.contains("Second line"));
        assertTrue(text.contains(flat(texts.get("section.comparison"))));
        assertTrue(text.contains(PdfReportWriter.CONTACT_EMAIL));
    }

    @Test
    void explainsMissingResults() throws IOException {
        Map<String, FcsSample> tubes = Map.of(
                "a", Tubes.tube("NC 1", 5, Map.of()),
                "b", Tubes.tube("TMZ 1", 40, Map.of()),
                "c", Tubes.tube("Unstained", 0, Map.of()));
        InMemorySampleStore store = new InMemorySampleStore();
        store.putAll(tubes);
        GatingTree tree = Tubes.gating()
                .add("p1", "Rect", new RectangleGate("FITC-A", "PI-A", 0, 1000, 0, 1000))
                .add("p1", "Poly", new PolygonGate("FITC-A", "PI-A", new double[] {0, 900, 0},
                        new double[] {0, 0, 900}))
                .addQuadrants("p1", List.of("Q1", "Q2", "Q3", "Q4"), "FITC-A", "PI-A", 100, 100);
        Experiment experiment = Experiment.empty("All").withGating(tree)
                .withSample(new SampleInfo("a", "NC 1", SampleRole.SAMPLE, "NC", 0, ""))
                .withSample(new SampleInfo("b", "TMZ 1", SampleRole.SAMPLE, "TMZ", 5, ""))
                .withSample(new SampleInfo("c", "Unstained", SampleRole.UNSTAINED, "",
                        Double.NaN, ""))
                .withCompensation(CompensationSource.EDITED,
                        SpilloverMatrix.identity(List.of("FITC-A", "PI-A")))
                .withApoptosis(Tubes.apoptosis())
                .withCellCycle(new CellCycleSettings("p1", "PI-A"))
                .withComparison(Metric.population("p2"), "PBS");
        ExperimentResults results = service.analyze(store, experiment);
        assertEquals(AnalysisIssue.NO_G1_PEAK, results.samples().get(0).cellCycleIssue());
        ReportTexts texts = new ReportTexts(Locale.ENGLISH, MONTREAL);
        List<Figure> figures = ReportFigures.of(experiment, results,
                id -> service.gate(store, experiment, id), texts);
        assertTrue(figures.size() >= 3);

        CytoReport report = CytoReport.builder(experiment, results).title(" ").analyst(" ")
                .figures(figures).build();
        assertEquals("CytoScope", report.title());
        assertTrue(report.analyst().isEmpty());
        assertTrue(report.notes().isEmpty());
        String text = text(new PdfReportWriter().toBytes(report), new int[1]);
        assertTrue(text.contains(flat(texts.of("issue", AnalysisIssue.NO_CONTROL_GROUP))));
        assertTrue(text.contains(flat(texts.of("issue", AnalysisIssue.TOO_FEW_DOSES))));
        assertTrue(text.contains(flat(texts.of("issue", AnalysisIssue.NO_G1_PEAK))));
        assertTrue(text.contains(flat(texts.get("fate." + CellFate.NECROTIC.name()))));

        Experiment noMetric = experiment.withComparison(null, "");
        ExperimentResults plain = service.analyze(store, noMetric);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new PdfReportWriter().write(CytoReport.builder(noMetric, plain).build(), out);
        String noComparison = text(out.toByteArray(), new int[1]);
        assertFalse(noComparison.contains(flat(texts.get("section.comparison"))));

        Experiment controlsOnly = Experiment.empty("All").withSample(experiment.samples().get(2));
        assertTrue(ReportFigures.of(controlsOnly, service.analyze(store, controlsOnly),
                id -> service.gate(store, controlsOnly, id), texts).isEmpty());
    }

    @Test
    void builderChecksItsArguments() {
        Experiment experiment = Experiment.empty("All");
        ExperimentResults results = new ExperimentResults(List.of(), null, null, null, null);
        assertThrows(NullPointerException.class, () -> CytoReport.builder(null, results));
        assertThrows(NullPointerException.class, () -> CytoReport.builder(experiment, null));
        assertThrows(NullPointerException.class,
                () -> CytoReport.builder(experiment, results).appVersion(null));
        assertThrows(NullPointerException.class,
                () -> CytoReport.builder(experiment, results).locale(null, MONTREAL));
        assertThrows(NullPointerException.class,
                () -> new PdfReportWriter().write(null, new ByteArrayOutputStream()));
        CytoReport report = CytoReport.builder(experiment, results).title(null).notes(null)
                .source("  Mendeley  ").build();
        assertEquals("Mendeley", report.source().orElseThrow());
        assertEquals("dev", report.appVersion());
        assertTrue(report.generatedAt().isAfter(WHEN.minusSeconds(1)));
        assertThrows(NullPointerException.class,
                () -> new PdfReportWriter().write(report, null));
    }

    @Test
    void formatsNumbersInTheReportLanguage() {
        ReportTexts english = new ReportTexts(Locale.ENGLISH, MONTREAL);
        ReportTexts french = new ReportTexts(CANADIAN_FRENCH, MONTREAL);
        assertEquals(Locale.ENGLISH, english.locale());
        assertEquals("1.50", english.number(1.5, 2));
        assertEquals("1,50", french.number(1.5, 2));
        assertEquals("–", english.number(Double.NaN, 1));
        assertEquals("–", english.pValue(Double.NaN));
        assertEquals("< 0.0001", english.pValue(1e-5));
        assertEquals("< 0,0001", french.pValue(1e-5));
        assertEquals("0.0123", english.pValue(0.0123));
        assertEquals("= 0.0123", english.pRelation(0.0123));
        assertEquals("< 0.0001", english.pRelation(1e-9));
        assertTrue(english.percent(12.5).startsWith("12.5"));
        assertTrue(french.percent(12.5).startsWith("12,5"));
        assertFalse(english.dateTime(WHEN).isBlank());

        GatingTree tree = Tubes.gating();
        assertTrue(english.metric(Metric.population("p1"), tree).contains("Cells"));
        assertTrue(english.metric(Metric.population("p7"), tree).contains("p7"));
        assertFalse(english.metric(Metric.apoptotic(), tree).isBlank());
        assertTrue(english.metric(Metric.fate(CellFate.LIVE), tree)
                .contains(english.get("fate.LIVE")));
        assertFalse(french.metric(Metric.phase(CellCyclePhase.S), tree).isBlank());
    }

    @Test
    void bothLanguagesHaveTheSameTexts() throws IOException {
        Properties english = load("report.properties");
        Properties french = load("report_fr.properties");
        assertEquals(english.keySet(), french.keySet());
    }

    private static Properties load(String name) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = ReportTexts.class.getResourceAsStream(name)) {
            properties.load(in);
        }
        return properties;
    }

    @Test
    void usesLetterPaperInNorthAmerica() {
        assertEquals(PDRectangle.LETTER, PdfReportWriter.pageSizeFor(Locale.US));
        assertEquals(PDRectangle.LETTER, PdfReportWriter.pageSizeFor(CANADIAN_FRENCH));
        assertEquals(PDRectangle.A4, PdfReportWriter.pageSizeFor(Locale.FRANCE));
        assertEquals(PDRectangle.A4, PdfReportWriter.pageSizeFor(Locale.ENGLISH));
    }
}
