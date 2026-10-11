package com.bioinfo.cytoscope.desktop;

import javafx.application.Application;

/**
 * Entry point that does not extend {@link Application}, so the app also starts from a plain
 * classpath.
 */
public final class DesktopLauncher {

    private DesktopLauncher() {}

    public static void main(String[] args) {
        Application.launch(DesktopApp.class, args);
    }
}
