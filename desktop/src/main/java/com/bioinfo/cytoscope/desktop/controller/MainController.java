package com.bioinfo.cytoscope.desktop.controller;

import com.bioinfo.cytoscope.desktop.AppInfo;
import com.bioinfo.cytoscope.desktop.help.HelpWindow;
import com.bioinfo.cytoscope.desktop.i18n.I18n;
import com.bioinfo.cytoscope.desktop.i18n.Messages;
import com.bioinfo.cytoscope.desktop.i18n.ViewTranslator;
import com.bioinfo.cytoscope.desktop.model.DesktopModel;
import com.bioinfo.cytoscope.desktop.model.UiSettings;
import com.bioinfo.cytoscope.desktop.view.Dialogs;
import com.bioinfo.cytoscope.service.Example;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TabPane;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Controller of the main window: menus, keyboard shortcuts, help and status bar.
 *
 * <p>Each tab has its own controller ({@link TubesTabController}, {@link
 * CompensationTabController}, {@link GatingTabController}, {@link AnalysisTabController}, {@link
 * ResultsTabController}, {@link ReportTabController}).
 */
public final class MainController {

    /** Help section of each tab, in tab order: F1 opens the section of the current tab. */
    public static final List<String> TAB_HELP_SECTIONS =
            List.of(
                    TubesTabController.HELP_SECTION,
                    CompensationTabController.HELP_SECTION,
                    GatingTabController.HELP_SECTION,
                    AnalysisTabController.HELP_SECTION,
                    ResultsTabController.HELP_SECTION,
                    ReportTabController.HELP_SECTION);

    private static final int TUBES_TAB = 0;

    private static final int RESULTS_TAB =
            TAB_HELP_SECTIONS.indexOf(ResultsTabController.HELP_SECTION);

    private static final int REPORT_TAB =
            TAB_HELP_SECTIONS.indexOf(ReportTabController.HELP_SECTION);

    private static final List<String> TEXT_SIZE_KEYS =
            List.of(
                    "menu.options.textSize.small",
                    "menu.options.textSize.normal",
                    "menu.options.textSize.large",
                    "menu.options.textSize.extraLarge");

    private static final List<KeyCode> DIGITS =
            List.of(
                    KeyCode.DIGIT1,
                    KeyCode.DIGIT2,
                    KeyCode.DIGIT3,
                    KeyCode.DIGIT4,
                    KeyCode.DIGIT5,
                    KeyCode.DIGIT6);

    private static final PseudoClass ERROR = PseudoClass.getPseudoClass("error");

    private final DesktopModel model;
    private final UiSettings settings;
    private final I18n i18n;
    private final Consumer<String> openUrl;
    private final String stylesheet;
    private HelpWindow helpWindow;

    @FXML private BorderPane root;
    @FXML private Menu languageMenu;
    @FXML private Menu textSizeMenu;
    @FXML private TabPane mainTabs;
    @FXML private ProgressIndicator busyIndicator;
    @FXML private Label statusLabel;

    /** Controllers of the included tabs, injected by the FXML loader. */
    @FXML private TubesTabController tubesTabController;

    @FXML private ResultsTabController resultsTabController;
    @FXML private ReportTabController reportTabController;

    /**
     * Creates the controller; the controls are injected afterwards by the FXML loader.
     *
     * @param model application state
     * @param settings language and text size
     * @param openUrl opens web pages, files and e-mail links with the default application
     * @param stylesheet application stylesheet, also applied to dialogs and the help window
     */
    public MainController(
            DesktopModel model, UiSettings settings, Consumer<String> openUrl, String stylesheet) {
        this.model = model;
        this.settings = settings;
        this.i18n = settings.i18n();
        this.openUrl = openUrl;
        this.stylesheet = stylesheet;
    }

    @FXML
    private void initialize() {
        buildLanguageMenu();
        buildTextSizeMenu();
        new ViewTranslator(i18n).translate(root);
        root.styleProperty()
                .bind(Bindings.format("-fx-font-size: %dpx;", settings.fontSizeProperty()));
        root.sceneProperty()
                .addListener(
                        (obs, old, scene) -> {
                            if (scene != null) {
                                installAccelerators(scene);
                            }
                        });
        mainTabs.getSelectionModel()
                .selectedIndexProperty()
                .addListener(
                        (obs, old, index) ->
                                resultsTabController.setActive(index.intValue() == RESULTS_TAB));

        busyIndicator.visibleProperty().bind(model.busyProperty());
        statusLabel
                .textProperty()
                .bind(
                        Bindings.createStringBinding(
                                () -> model.statusProperty().get().render(i18n.messages()),
                                model.statusProperty(),
                                i18n.messagesProperty()));
        model.statusProperty()
                .addListener(
                        (obs, old, status) ->
                                statusLabel.pseudoClassStateChanged(ERROR, status.error()));
    }

    // ----- Menus and shortcuts -----

    /**
     * Lists each language in itself ("English", "Français"), so that users find theirs whatever the
     * current language.
     */
    private void buildLanguageMenu() {
        ToggleGroup group = new ToggleGroup();
        for (Locale locale : Messages.SUPPORTED_LOCALES) {
            String name = locale.getDisplayLanguage(locale);
            String label = name.substring(0, 1).toUpperCase(locale) + name.substring(1);
            RadioMenuItem item = new RadioMenuItem(label);
            item.setUserData(locale);
            item.setToggleGroup(group);
            item.setOnAction(e -> settings.setLocale(locale));
            languageMenu.getItems().add(item);
        }
        selectToggle(group, i18n.locale()::equals);
    }

    /** Preset text sizes, followed by zoom in, zoom out and reset. */
    private void buildTextSizeMenu() {
        ToggleGroup group = new ToggleGroup();
        for (int i = 0; i < UiSettings.PRESET_FONT_SIZES.size(); i++) {
            int size = UiSettings.PRESET_FONT_SIZES.get(i);
            RadioMenuItem item = new RadioMenuItem();
            item.textProperty().bind(i18n.text(TEXT_SIZE_KEYS.get(i)));
            item.setUserData(size);
            item.setToggleGroup(group);
            item.setOnAction(e -> settings.setFontSize(size));
            textSizeMenu.getItems().add(item);
        }
        textSizeMenu
                .getItems()
                .addAll(
                        new SeparatorMenuItem(),
                        menuItem(
                                "menu.options.zoomIn",
                                "Shortcut+Equals",
                                settings::increaseFontSize),
                        menuItem(
                                "menu.options.zoomOut",
                                "Shortcut+Minus",
                                settings::decreaseFontSize),
                        menuItem("menu.options.zoomReset", "Shortcut+0", settings::resetFontSize));
        settings.fontSizeProperty()
                .addListener(
                        (obs, old, size) ->
                                selectToggle(group, Integer.valueOf(size.intValue())::equals));
        selectToggle(group, Integer.valueOf(settings.fontSize())::equals);
    }

    private MenuItem menuItem(String key, String accelerator, Runnable action) {
        MenuItem item = Controls.menuItem(key, i18n, action);
        item.setAccelerator(KeyCombination.keyCombination(accelerator));
        return item;
    }

    /** Shortcuts without a menu item: Ctrl+1…6 select the tabs, the numeric keypad zooms. */
    private void installAccelerators(Scene scene) {
        for (int i = 0; i < DIGITS.size(); i++) {
            int tab = i;
            shortcut(scene, DIGITS.get(i), () -> mainTabs.getSelectionModel().select(tab));
        }
        shortcut(scene, KeyCode.ADD, settings::increaseFontSize);
        shortcut(scene, KeyCode.PLUS, settings::increaseFontSize);
        shortcut(scene, KeyCode.SUBTRACT, settings::decreaseFontSize);
        shortcut(scene, KeyCode.NUMPAD0, settings::resetFontSize);
    }

    private static void shortcut(Scene scene, KeyCode key, Runnable action) {
        scene.getAccelerators()
                .put(new KeyCodeCombination(key, KeyCombination.SHORTCUT_DOWN), action);
    }

    private static void selectToggle(ToggleGroup group, Predicate<Object> matches) {
        Toggle match =
                group.getToggles().stream()
                        .filter(toggle -> matches.test(toggle.getUserData()))
                        .findFirst()
                        .orElse(null);
        group.selectToggle(match);
    }

    // ----- "File" menu -----

    @FXML
    private void onNew() {
        model.newExperiment(Texts.rootName(i18n.messages()));
        mainTabs.getSelectionModel().select(TUBES_TAB);
    }

    @FXML
    private void onOpen() {
        mainTabs.getSelectionModel().select(TUBES_TAB);
        tubesTabController.open();
    }

    @FXML
    private void onApoptosisExample() {
        openExample(Example.U87_APOPTOSIS);
    }

    @FXML
    private void onCellCycleExample() {
        openExample(Example.U87_CELL_CYCLE);
    }

    @FXML
    private void onCadmiumExample() {
        openExample(Example.CADMIUM);
    }

    private void openExample(Example example) {
        mainTabs.getSelectionModel().select(TUBES_TAB);
        tubesTabController.openExample(example);
    }

    @FXML
    private void onSaveReport() {
        mainTabs.getSelectionModel().select(REPORT_TAB);
        reportTabController.save();
    }

    @FXML
    private void onQuit() {
        Platform.exit();
    }

    // ----- "Help" menu -----

    /**
     * Opens the user guide at a section.
     *
     * @param section identifier of the section, such as {@code "gating"}
     */
    public void showHelp(String section) {
        helpWindow().show(section);
    }

    @FXML
    private void onHelp() {
        int tab = Math.max(0, mainTabs.getSelectionModel().getSelectedIndex());
        showHelp(TAB_HELP_SECTIONS.get(tab));
    }

    @FXML
    private void onShortcuts() {
        dialogs().showShortcuts(i18n.messages());
    }

    @FXML
    private void onAbout() {
        dialogs().showAbout(i18n.messages(), openUrl);
    }

    @FXML
    private void onOpenFcsStandard() {
        openUrl.accept(AppInfo.FCS_STANDARD_URL);
    }

    @FXML
    private void onOpenDataset() {
        openUrl.accept(AppInfo.DATASET_URL);
    }

    /**
     * Returns the help window, created on first use.
     *
     * @return the help window of this main window
     */
    public HelpWindow helpWindow() {
        if (helpWindow == null) {
            helpWindow = new HelpWindow(root.getScene().getWindow(), settings, stylesheet, openUrl);
        }
        return helpWindow;
    }

    private Dialogs dialogs() {
        return new Dialogs(root.getScene().getWindow(), root, stylesheet);
    }
}
