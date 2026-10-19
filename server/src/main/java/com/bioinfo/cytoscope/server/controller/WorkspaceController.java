package com.bioinfo.cytoscope.server.controller;

import com.bioinfo.cytoscope.analysis.ApoptosisSettings;
import com.bioinfo.cytoscope.compensation.SpilloverMatrix;
import com.bioinfo.cytoscope.experiment.CompensationSource;
import com.bioinfo.cytoscope.experiment.Experiment;
import com.bioinfo.cytoscope.experiment.ExperimentResults;
import com.bioinfo.cytoscope.experiment.SampleInfo;
import com.bioinfo.cytoscope.fcs.FcsSample;
import com.bioinfo.cytoscope.fcs.FcsWriter;
import com.bioinfo.cytoscope.gating.GatingResult;
import com.bioinfo.cytoscope.gating.Population;
import com.bioinfo.cytoscope.report.CytoReport;
import com.bioinfo.cytoscope.report.Figure;
import com.bioinfo.cytoscope.report.PdfReportWriter;
import com.bioinfo.cytoscope.report.ReportFigures;
import com.bioinfo.cytoscope.report.ReportTexts;
import com.bioinfo.cytoscope.server.HttpError;
import com.bioinfo.cytoscope.server.JsonHandler;
import com.bioinfo.cytoscope.server.api.ExperimentJson;
import com.bioinfo.cytoscope.server.billing.BillingService;
import com.bioinfo.cytoscope.server.api.PlotView;
import com.bioinfo.cytoscope.server.api.ResultsView;
import com.bioinfo.cytoscope.server.api.WorkspaceView;
import com.bioinfo.cytoscope.server.workspace.Workspace;
import com.bioinfo.cytoscope.server.workspace.WorkspaceRegistry;
import com.bioinfo.cytoscope.service.CytoService;
import com.bioinfo.cytoscope.service.Example;
import com.bioinfo.cytoscope.service.ExampleLibrary;
import java.io.IOException;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Routes of a workspace, the files and set-up of one visitor:
 *
 * <ul>
 *   <li>{@code POST /api/workspaces}: opens an empty workspace or an example;
 *   <li>{@code GET} / {@code DELETE /api/workspaces/{id}}: reads or closes it;
 *   <li>{@code POST /api/workspaces/{id}/samples?name=}: loads an FCS file (raw body);
 *   <li>{@code DELETE /api/workspaces/{id}/samples/{sample}}: removes a tube;
 *   <li>{@code GET /api/workspaces/{id}/samples/{sample}/fcs?population=}: exports the events of
 *       a population as an FCS file;
 *   <li>{@code PUT /api/workspaces/{id}/experiment}: replaces the set-up;
 *   <li>{@code POST /api/workspaces/{id}/compensation/controls}: computes the matrix from the
 *       single stains;
 *   <li>{@code POST /api/workspaces/{id}/compensation/file}: uses the matrix of the files;
 *   <li>{@code POST /api/workspaces/{id}/apoptosis/suggest}: proposes the thresholds of the
 *       annexin V / PI test;
 *   <li>{@code POST /api/workspaces/{id}/plot}: counts the events of a plot;
 *   <li>{@code GET /api/workspaces/{id}/results}: runs every analysis;
 *   <li>{@code POST /api/workspaces/{id}/report}: renders the PDF report.
 * </ul>
 */
public final class WorkspaceController extends JsonHandler {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Montreal");

    private final CytoService service;
    private final WorkspaceRegistry registry;
    private final BillingService billing;
    private final ExampleLibrary examples;
    private final String version;

    /**
     * Opens a workspace.
     *
     * @param example folder of the example to open; null or empty for an empty workspace
     * @param language {@code en} or {@code fr}, for the names of the populations
     */
    public record CreateRequest(String example, String language) {}

    /**
     * Asks for the report.
     *
     * @param title title of the report; blank for the default
     * @param analyst person who ran the analysis; blank for none
     * @param notes free text printed at the end; blank for none
     * @param language {@code en} or {@code fr}
     * @param timeZone time zone of the dates, such as {@code America/Montreal}; null for Québec
     */
    public record ReportRequest(String title, String analyst, String notes, String language,
                                String timeZone) {}

    /**
     * Creates the controller.
     *
     * @param service the analyses
     * @param registry the workspaces
     * @param billing credits and free trial
     * @param version version of the application, printed in the reports
     */
    public WorkspaceController(CytoService service, WorkspaceRegistry registry,
                               BillingService billing, String version) {
        super("/api/workspaces");
        this.service = service;
        this.registry = registry;
        this.billing = billing;
        this.examples = new ExampleLibrary(service);
        this.version = version;
    }

    @Override
    protected Object handle(Request request) throws IOException {
        List<String> path = request.segments();
        if (path.isEmpty()) {
            requireMethod(request, "POST");
            return create(request.json(CreateRequest.class));
        }
        if (path.size() == 1 && request.method().equals("DELETE")) {
            registry.delete(path.get(0));
            return Map.of("deleted", true);
        }
        Workspace workspace = registry.get(path.get(0));
        String route = path.size() == 1 ? "" : path.get(1);
        return switch (route) {
            case "" -> {
                requireMethod(request, "GET");
                yield view(workspace);
            }
            case "samples" -> samples(request, workspace, path);
            case "experiment" -> {
                requireMethod(request, "PUT");
                yield replace(workspace, request.json(ExperimentJson.class));
            }
            case "compensation" -> {
                requireMethod(request, "POST");
                if (path.size() == 3 && path.get(2).equals("file")) {
                    SpilloverMatrix matrix = workspace.samples().values().stream()
                            .map(SpilloverMatrix::fromSample).flatMap(Optional::stream)
                            .findFirst().orElseThrow(() -> new IllegalArgumentException(
                                    "No file holds a spillover matrix"));
                    workspace.update(e -> e.withCompensation(CompensationSource.FILE, matrix));
                } else {
                    requireRoute(request, path, 3, "controls", "POST");
                    workspace.update(e -> e.withCompensation(CompensationSource.CONTROLS,
                            service.matrixFromControls(workspace, e)));
                }
                yield view(workspace);
            }
            case "apoptosis" -> {
                requireRoute(request, path, 3, "suggest", "POST");
                ApoptosisSettings settings = request.json(ApoptosisSettings.class);
                workspace.update(e -> e.withApoptosis(
                        service.suggestThresholds(workspace, e, settings)));
                yield view(workspace);
            }
            case "plot" -> {
                requireMethod(request, "POST");
                yield plot(workspace, request.json(PlotView.Request.class));
            }
            case "results" -> {
                requireMethod(request, "GET");
                yield ResultsView.of(service.analyze(workspace, workspace.experiment()));
            }
            case "report" -> {
                requireMethod(request, "POST");
                yield report(workspace, request.json(ReportRequest.class));
            }
            default -> throw notFound(request);
        };
    }

    private WorkspaceView create(CreateRequest request) {
        String language = language(request.language());
        boolean french = language.equals("fr");
        String root = french ? "Tous les événements" : "All events";
        if (request.example() == null || request.example().isBlank()) {
            return view(registry.create(null, language, Experiment.empty(root), Map.of()));
        }
        Example example = Example.fromFolder(request.example());
        int[] next = {0};
        ExampleLibrary.OpenedExample opened = examples.open(example, root,
                french ? ExampleLibrary.PresetNames.FRENCH : ExampleLibrary.PresetNames.ENGLISH,
                () -> "s" + ++next[0]);
        return view(registry.create(example, language, opened.experiment(), opened.samples()));
    }

    private Object samples(Request request, Workspace workspace, List<String> path)
            throws IOException {
        if (path.size() == 2) {
            requireMethod(request, "POST");
            String fileName = request.param("name", "sample.fcs");
            FcsSample sample = service.read(fileName, request.upload());
            billing.authorizeUpload(workspace, sample, request.param("accessCode", ""));
            String id = workspace.nextSampleId();
            registry.store(workspace, id, sample);
            workspace.update(e -> service.addSample(e, id, sample));
            return view(workspace);
        }
        String sampleId = path.get(2);
        if (path.size() == 3) {
            requireMethod(request, "DELETE");
            if (!workspace.remove(sampleId)) {
                throw new HttpError(404, "Unknown tube " + sampleId);
            }
            return view(workspace);
        }
        requireRoute(request, path, 4, "fcs", "GET");
        String population = request.param("population", Population.ROOT_ID);
        GatingResult gating = service.gate(workspace, workspace.experiment(), sampleId);
        if (gating.tree().find(population).isEmpty()) {
            throw new IllegalArgumentException("Unknown population " + population);
        }
        FcsSample raw = workspace.require(sampleId);
        String populationName = gating.tree().find(population).orElseThrow().name();
        String name = population.equals(Population.ROOT_ID) ? raw.name()
                : raw.name() + " - " + populationName;
        return new FileResponse(FcsWriter.write(raw.withName(name), gating.events(population)),
                "application/vnd.isac.fcs", name + ".fcs");
    }

    private WorkspaceView replace(Workspace workspace, ExperimentJson json) {
        Experiment experiment = json.toExperiment();
        Set<String> loaded = workspace.samples().keySet();
        Set<String> sent = new HashSet<>();
        for (SampleInfo info : experiment.samples()) {
            sent.add(info.id());
        }
        if (!sent.equals(loaded)) {
            throw new IllegalArgumentException(
                    "The experiment must list exactly the tubes loaded in the workspace");
        }
        workspace.update(e -> experiment);
        return view(workspace);
    }

    private PlotView plot(Workspace workspace, PlotView.Request request) {
        if (request.sampleId() == null) {
            throw new IllegalArgumentException("Choose a tube");
        }
        Experiment experiment = workspace.experiment();
        return PlotView.of(request, experiment,
                service.gate(workspace, experiment, request.sampleId()));
    }

    private FileResponse report(Workspace workspace, ReportRequest request) throws IOException {
        Locale locale = language(request.language()).equals("fr") ? Locale.CANADA_FRENCH
                : Locale.CANADA;
        ZoneId zone = DEFAULT_ZONE;
        if (request.timeZone() != null && !request.timeZone().isBlank()) {
            try {
                zone = ZoneId.of(request.timeZone());
            } catch (DateTimeException e) {
                throw new IllegalArgumentException("Unknown time zone " + request.timeZone(), e);
            }
        }
        Experiment experiment = workspace.experiment();
        ExperimentResults results = service.analyze(workspace, experiment);
        ReportTexts texts = new ReportTexts(locale, zone);
        List<Figure> figures = ReportFigures.of(experiment, results,
                id -> service.gate(workspace, experiment, id), texts);
        String source = workspace.example().map(e -> texts.get(e.simulated()
                ? "source.simulated" : "source.mendeley")).orElse(null);
        CytoReport report = CytoReport.builder(experiment, results).title(request.title())
                .analyst(request.analyst()).notes(request.notes()).source(source)
                .locale(locale, zone).appVersion(version).figures(figures).build();
        byte[] pdf = new PdfReportWriter().toBytes(report);
        String fileName = report.title().replaceAll("[\\\\/:*?\"<>|]", "_") + ".pdf";
        return new FileResponse(pdf, "application/pdf", fileName);
    }

    private WorkspaceView view(Workspace workspace) {
        return WorkspaceView.of(workspace, registry.timeToLive().toMinutes());
    }

    private static String language(String language) {
        return language != null && language.toLowerCase(Locale.ROOT).startsWith("fr") ? "fr"
                : "en";
    }

    /** Checks that the path has a given length and ends with an action, such as {@code fcs}. */
    private static void requireRoute(Request request, List<String> path, int length,
                                     String action, String method) {
        if (path.size() != length || !path.get(length - 1).equals(action)) {
            throw notFound(request);
        }
        requireMethod(request, method);
    }
}
