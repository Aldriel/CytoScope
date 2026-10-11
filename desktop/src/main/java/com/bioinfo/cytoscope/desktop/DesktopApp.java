package com.bioinfo.cytoscope.desktop;

import com.bioinfo.cytoscope.desktop.controller.ControllerFactory;
import com.bioinfo.cytoscope.desktop.model.DesktopModel;
import com.bioinfo.cytoscope.desktop.model.UiSettings;
import com.bioinfo.cytoscope.desktop.prefs.UserPreferences;
import com.bioinfo.cytoscope.desktop.view.FilePicker;
import com.bioinfo.cytoscope.desktop.view.NativeFilePicker;
import com.bioinfo.cytoscope.service.CytoService;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** JavaFX application: wires Model (service + observable state), View (FXML) and Controller. */
public final class DesktopApp extends Application {

    /** Title of the main window. */
    public static final String TITLE = AppInfo.NAME;

    /** Stylesheet of every window of the application. */
    public static final String STYLESHEET = resource("view/styles.css");

    private static final List<Integer> ICON_SIZES = List.of(16, 32, 48, 64, 256);

    private ExecutorService background;
    private UiSettings settings;
    private Stage stage;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        background = Executors.newVirtualThreadPerTaskExecutor();
        settings = new UiSettings(UserPreferences.load());
        UserPreferences prefs = settings.preferences();
        DesktopModel model =
                new DesktopModel(
                        new CytoService(),
                        background,
                        Platform::runLater,
                        AppInfo.version(),
                        settings.i18n().messages().get("population.root"));

        stage.setTitle(TITLE);
        ICON_SIZES.forEach(
                size -> stage.getIcons().add(new Image(resource("view/icon-" + size + ".png"))));
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.setScene(
                createScene(
                        model,
                        settings,
                        getHostServices()::showDocument,
                        new NativeFilePicker(prefs),
                        prefs.windowWidth(),
                        prefs.windowHeight()));
        stage.setMaximized(prefs.windowMaximized());
        stage.show();
    }

    /**
     * Loads the FXML views and binds them to {@code model} through their controllers.
     *
     * @param model application state
     * @param settings language and text size
     * @param openUrl opens web pages, files and e-mail links with the default application
     * @param filePicker asks for the files to read and write
     * @param width initial width of the scene
     * @param height initial height of the scene
     * @return the scene of the main window; its user data is the {@code MainController}
     */
    public static Scene createScene(
            DesktopModel model,
            UiSettings settings,
            Consumer<String> openUrl,
            FilePicker filePicker,
            double width,
            double height) {
        FXMLLoader loader = new FXMLLoader(DesktopApp.class.getResource("view/main-view.fxml"));
        loader.setControllerFactory(
                new ControllerFactory(model, settings, openUrl, STYLESHEET, filePicker));
        Parent root;
        try {
            root = loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Scene scene = new Scene(root, width, height);
        scene.getStylesheets().add(STYLESHEET);
        scene.setUserData(loader.getController());
        return scene;
    }

    @Override
    public void stop() {
        if (stage != null && stage.getScene() != null && settings != null) {
            settings.preferences()
                    .setWindowState(
                            stage.getScene().getWidth(),
                            stage.getScene().getHeight(),
                            stage.isMaximized());
            settings.preferences().flush();
        }
        if (background != null) {
            background.shutdownNow();
        }
    }

    private static String resource(String name) {
        URL url = Objects.requireNonNull(DesktopApp.class.getResource(name), name);
        return url.toExternalForm();
    }
}
