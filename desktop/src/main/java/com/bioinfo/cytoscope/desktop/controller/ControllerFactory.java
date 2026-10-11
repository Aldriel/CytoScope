package com.bioinfo.cytoscope.desktop.controller;

import com.bioinfo.cytoscope.desktop.i18n.I18n;
import com.bioinfo.cytoscope.desktop.model.DesktopModel;
import com.bioinfo.cytoscope.desktop.model.UiSettings;
import com.bioinfo.cytoscope.desktop.view.FilePicker;

import javafx.util.Callback;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Creates the controllers declared in the FXML views and gives them the model they work on.
 *
 * <p>The main view is loaded first, so the main controller exists when the tabs are created: the
 * "?" button of each tab opens the help through it.
 */
public final class ControllerFactory implements Callback<Class<?>, Object> {

    private final DesktopModel model;
    private final UiSettings settings;
    private final Consumer<String> openUrl;
    private final String stylesheet;
    private final FilePicker filePicker;
    private MainController main;

    /**
     * Creates a factory for one main window.
     *
     * @param model application state shared by all the controllers
     * @param settings language and text size
     * @param openUrl opens web pages, files and e-mail links with the default application
     * @param stylesheet application stylesheet, also applied to dialogs and the help window
     * @param filePicker asks for the files to read and write
     */
    public ControllerFactory(
            DesktopModel model,
            UiSettings settings,
            Consumer<String> openUrl,
            String stylesheet,
            FilePicker filePicker) {
        this.model = Objects.requireNonNull(model, "model");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.openUrl = Objects.requireNonNull(openUrl, "openUrl");
        this.stylesheet = Objects.requireNonNull(stylesheet, "stylesheet");
        this.filePicker = Objects.requireNonNull(filePicker, "filePicker");
    }

    @Override
    public Object call(Class<?> type) {
        if (type == MainController.class) {
            main = new MainController(model, settings, openUrl, stylesheet);
            return main;
        }
        Consumer<String> showHelp = section -> main.showHelp(section);
        I18n i18n = settings.i18n();
        if (type == TubesTabController.class) {
            return new TubesTabController(model, i18n, showHelp, filePicker, openUrl);
        }
        if (type == CompensationTabController.class) {
            return new CompensationTabController(model, i18n, showHelp);
        }
        if (type == GatingTabController.class) {
            return new GatingTabController(model, i18n, showHelp, filePicker);
        }
        if (type == AnalysisTabController.class) {
            return new AnalysisTabController(model, i18n, showHelp);
        }
        if (type == ResultsTabController.class) {
            return new ResultsTabController(model, i18n, showHelp);
        }
        if (type == ReportTabController.class) {
            return new ReportTabController(
                    model, i18n, showHelp, filePicker, openUrl, Locale.getDefault());
        }
        if (type == FooterController.class) {
            return new FooterController(i18n, openUrl);
        }
        throw new IllegalArgumentException("Unexpected controller " + type);
    }
}
