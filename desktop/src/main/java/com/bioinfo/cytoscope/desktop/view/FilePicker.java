package com.bioinfo.cytoscope.desktop.view;

import com.bioinfo.cytoscope.desktop.i18n.Messages;

import javafx.stage.Window;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Asks the user for the files to read and write. The application uses the file choosers of the
 * operating system ({@link NativeFilePicker}); the functional tests answer without a window.
 */
public interface FilePicker {

    /**
     * Asks for FCS files to open.
     *
     * @param owner window owning the dialog
     * @param m messages in the current language
     * @return the files, or an empty list if the user cancels
     */
    List<Path> chooseFcsFiles(Window owner, Messages m);

    /**
     * Asks where to save the events of a population as an FCS file.
     *
     * @param owner window owning the dialog
     * @param m messages in the current language
     * @param suggestedName name proposed for the file
     * @return the file, or empty if the user cancels
     */
    Optional<Path> chooseFcsExport(Window owner, Messages m, String suggestedName);

    /**
     * Asks where to save the PDF report.
     *
     * @param owner window owning the dialog
     * @param m messages in the current language
     * @param suggestedName name proposed for the file
     * @return the file, or empty if the user cancels
     */
    Optional<Path> chooseReport(Window owner, Messages m, String suggestedName);
}
