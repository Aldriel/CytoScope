package com.bioinfo.cytoscope.desktop.controller;

import com.bioinfo.cytoscope.desktop.AppInfo;
import com.bioinfo.cytoscope.desktop.i18n.I18n;

import javafx.fxml.FXML;
import javafx.scene.control.Hyperlink;

import java.util.function.Consumer;

/**
 * Controller of the footer of the main window: opens the author's website (in the current
 * language), writes an e-mail to the author, opens the licence and the Ko-fi support page.
 */
public final class FooterController {

    private final I18n i18n;
    private final Consumer<String> openUrl;

    @FXML private Hyperlink emailLink;

    /**
     * Creates the controller; the controls are injected afterwards by the FXML loader.
     *
     * @param i18n current language, which chooses the version of the website
     * @param openUrl opens web pages and e-mail links with the default application
     */
    public FooterController(I18n i18n, Consumer<String> openUrl) {
        this.i18n = i18n;
        this.openUrl = openUrl;
    }

    @FXML
    private void initialize() {
        emailLink.setText(AppInfo.CONTACT_EMAIL);
    }

    @FXML
    private void onOpenWebsite() {
        openUrl.accept(i18n.messages().get("about.website"));
    }

    @FXML
    private void onWriteEmail() {
        openUrl.accept("mailto:" + AppInfo.CONTACT_EMAIL);
    }

    @FXML
    private void onSupport() {
        openUrl.accept(AppInfo.KOFI_URL);
    }

    @FXML
    private void onReadLicense() {
        openUrl.accept(AppInfo.LICENSE_URL);
    }
}
