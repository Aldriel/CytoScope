package com.bioinfo.cytoscope.desktop.controller;

import com.bioinfo.cytoscope.desktop.i18n.I18n;
import com.bioinfo.cytoscope.desktop.i18n.Messages;
import com.bioinfo.cytoscope.desktop.model.DesktopModel;
import com.bioinfo.cytoscope.desktop.model.ReportOptions;
import com.bioinfo.cytoscope.desktop.view.FilePicker;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Controller of the "Report" tab: title, language, paper size, analyst and notes of the PDF
 * report, which is opened once saved.
 */
public final class ReportTabController {

    /** Section of the user guide describing this tab. */
    public static final String HELP_SECTION = "report";

    /** Paper size of the report; the country of the report locale chooses it. */
    enum Paper {
        /** US Letter, used in North America. */
        LETTER,
        /** A4, used elsewhere. */
        A4;

        /** Returns the paper used in the country of {@code locale}. */
        static Paper of(Locale locale) {
            return LETTER_COUNTRIES.contains(locale.getCountry()) ? LETTER : A4;
        }
    }

    /** Countries using the US Letter size, as in {@code PdfReportWriter}. */
    private static final Set<String> LETTER_COUNTRIES = Set.of("US", "CA");

    private final DesktopModel model;
    private final I18n i18n;
    private final Consumer<String> showHelp;
    private final FilePicker filePicker;
    private final Consumer<String> openUrl;
    private final Locale systemLocale;

    @FXML private TextField titleField;
    @FXML private ComboBox<Locale> languageBox;
    @FXML private ComboBox<Paper> paperBox;
    @FXML private TextField analystField;
    @FXML private TextArea notesArea;
    @FXML private Button saveButton;
    @FXML private Label readinessLabel;

    /**
     * Creates the controller; the controls are injected afterwards by the FXML loader.
     *
     * @param model application state
     * @param i18n current language, also the default language of the report
     * @param showHelp opens the user guide at the given section
     * @param filePicker asks where to save the report
     * @param openUrl opens the saved report with the default PDF reader
     * @param systemLocale locale of the computer, whose country chooses the default paper size
     */
    public ReportTabController(
            DesktopModel model,
            I18n i18n,
            Consumer<String> showHelp,
            FilePicker filePicker,
            Consumer<String> openUrl,
            Locale systemLocale) {
        this.model = model;
        this.i18n = i18n;
        this.showHelp = showHelp;
        this.filePicker = filePicker;
        this.openUrl = openUrl;
        this.systemLocale = systemLocale;
    }

    @FXML
    private void initialize() {
        Controls.translatedChoices(
                languageBox,
                new Locale[] {Locale.ENGLISH, Locale.FRENCH},
                i18n.locale(),
                locale -> "reportLanguage." + locale.getLanguage(),
                i18n);
        Controls.translatedChoices(
                paperBox, Paper.values(), Paper.of(systemLocale), p -> "paper." + p.name(), i18n);
        model.experimentProperty().addListener((obs, old, experiment) -> renderReadiness());
        i18n.messagesProperty().addListener((obs, old, messages) -> renderReadiness());
        renderReadiness();
    }

    /** Asks where to save the report, writes it and opens it. */
    public void save() {
        if (model.experimentProperty().get().samples().isEmpty()) {
            return;
        }
        Messages m = i18n.messages();
        ReportOptions options = options();
        filePicker
                .chooseReport(
                        saveButton.getScene().getWindow(),
                        m,
                        options.title().replaceAll("[\\\\/:*?\"<>|]", "_") + ".pdf")
                .ifPresent(
                        file ->
                                model.writeReport(
                                        file,
                                        options,
                                        saved -> openUrl.accept(saved.toUri().toString())));
    }

    /** Returns the content of the report chosen in the tab. */
    ReportOptions options() {
        Locale language = languageBox.getValue();
        String country =
                paperBox.getValue() == Paper.LETTER
                        ? "CA"
                        : (language.getLanguage().equals("fr") ? "FR" : "GB");
        String title = titleField.getText().strip();
        return new ReportOptions(
                title.isEmpty() ? i18n.messages().get("report.title.prompt") : title,
                Locale.of(language.getLanguage(), country),
                analystField.getText(),
                notesArea.getText());
    }

    @FXML
    private void onSave() {
        save();
    }

    @FXML
    private void onHelp() {
        showHelp.accept(HELP_SECTION);
    }

    private void renderReadiness() {
        int tubes = model.experimentProperty().get().samples().size();
        saveButton.setDisable(tubes == 0);
        Messages m = i18n.messages();
        readinessLabel.setText(
                tubes == 0 ? m.get("report.needsTubes") : m.get("report.ready", tubes));
    }
}
