package com.bioinfo.cytoscope.server.controller;

import com.bioinfo.cytoscope.server.JsonHandler;
import com.bioinfo.cytoscope.service.Example;
import com.bioinfo.cytoscope.service.ExampleLibrary;
import java.util.Arrays;
import java.util.List;

/**
 * {@code GET /api/examples}: the example experiments, with the credit of the real data. An
 * example is opened with {@code POST /api/workspaces}.
 */
public final class ExampleController extends JsonHandler {

    /**
     * The examples.
     *
     * @param examples each example
     * @param attribution credit of the real data, in Markdown
     */
    public record ExamplesView(List<ExampleView> examples, String attribution) {}

    /**
     * One example.
     *
     * @param folder identifier of the example
     * @param simulated true if its data are simulated
     * @param tubes number of tubes
     */
    public record ExampleView(String folder, boolean simulated, int tubes) {}

    /** Creates the controller. */
    public ExampleController() {
        super("/api/examples");
    }

    @Override
    protected Object handle(Request request) {
        if (!request.segments().isEmpty()) {
            throw notFound(request);
        }
        requireMethod(request, "GET");
        List<ExampleView> examples = Arrays.stream(Example.values())
                .map(e -> new ExampleView(e.folder(), e.simulated(),
                        ExampleLibrary.samples(e).size())).toList();
        return new ExamplesView(examples, ExampleLibrary.attribution());
    }
}
