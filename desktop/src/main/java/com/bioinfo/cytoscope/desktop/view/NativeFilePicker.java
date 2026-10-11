package com.bioinfo.cytoscope.desktop.view;

import com.bioinfo.cytoscope.desktop.i18n.Messages;
import com.bioinfo.cytoscope.desktop.prefs.UserPreferences;

import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * File choosers of the operating system. They open in the folder used last time, remembered in the
 * user preferences.
 */
public final class NativeFilePicker implements FilePicker {

    private final UserPreferences preferences;

    /**
     * Creates the picker.
     *
     * @param preferences where the last folders are remembered
     */
    public NativeFilePicker(UserPreferences preferences) {
        this.preferences = preferences;
    }

    @Override
    public List<Path> chooseFcsFiles(Window owner, Messages m) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(m.get("chooser.fcs.title"));
        chooser.getExtensionFilters()
                .addAll(
                        new FileChooser.ExtensionFilter(m.get("chooser.fcs.filter"), "*.fcs"),
                        new FileChooser.ExtensionFilter(m.get("chooser.all"), "*.*"));
        preferences.lastDirectory().map(Path::toFile).ifPresent(chooser::setInitialDirectory);
        List<File> files = chooser.showOpenMultipleDialog(owner);
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        Path folder = files.getFirst().toPath().getParent();
        if (folder != null) {
            preferences.setLastDirectory(folder);
        }
        return files.stream().map(File::toPath).toList();
    }

    @Override
    public Optional<Path> chooseFcsExport(Window owner, Messages m, String suggestedName) {
        return save(owner, m.get("chooser.export.title"), suggestedName,
                new FileChooser.ExtensionFilter(m.get("chooser.fcs.filter"), "*.fcs"));
    }

    @Override
    public Optional<Path> chooseReport(Window owner, Messages m, String suggestedName) {
        return save(owner, m.get("chooser.report.title"), suggestedName,
                new FileChooser.ExtensionFilter(m.get("chooser.report.filter"), "*.pdf"));
    }

    private Optional<Path> save(
            Window owner, String title, String suggestedName, FileChooser.ExtensionFilter filter) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.setInitialFileName(suggestedName);
        chooser.getExtensionFilters().add(filter);
        preferences.saveDirectory().map(Path::toFile).ifPresent(chooser::setInitialDirectory);
        File file = chooser.showSaveDialog(owner);
        if (file == null) {
            return Optional.empty();
        }
        Path path = file.toPath();
        if (path.getParent() != null) {
            preferences.setSaveDirectory(path.getParent());
        }
        return Optional.of(path);
    }
}
