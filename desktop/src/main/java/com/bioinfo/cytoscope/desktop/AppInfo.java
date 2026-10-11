package com.bioinfo.cytoscope.desktop;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Name, version and author of the application. */
public final class AppInfo {

    /** Name of the application, shown in the title bar and the About dialog. */
    public static final String NAME = "CytoScope";

    /** Author of the application. */
    public static final String AUTHOR = "Maxime Ethier";

    /** Contact address shown in the About dialog. */
    public static final String CONTACT_EMAIL = "contact@maximeethier.com";

    /** Page where users can support the author with a donation. */
    public static final String KOFI_URL = "https://ko-fi.com/K1S228CL7A";

    /** Text of the licence under which the application is distributed. */
    public static final String LICENSE_URL = "https://www.apache.org/licenses/LICENSE-2.0";

    /** Real example data: U-87MG cells, Hassan et al. (2026), Mendeley Data, CC BY 4.0. */
    public static final String DATASET_URL = "https://doi.org/10.17632/d4r5h95356.1";

    /** Data standards of the ISAC, among which the FCS format. */
    public static final String FCS_STANDARD_URL = "https://isac-net.org/page/Data-Standards";

    private static final String VERSION = loadVersion();

    private AppInfo() {}

    /** Returns the version of the application, set by the Maven build. */
    public static String version() {
        return VERSION;
    }

    /**
     * Returns the version written into {@code app.properties} by the build, or {@code "dev"}
     * outside a Maven build.
     */
    private static String loadVersion() {
        return readVersion(AppInfo.class.getResourceAsStream("app.properties"));
    }

    /**
     * Reads the {@code version} property of a properties file.
     *
     * @param in the file, or {@code null} when it is missing; closed by this method
     * @return the version, or {@code "dev"} when the file is missing, unreadable or not filtered
     */
    static String readVersion(InputStream in) {
        if (in == null) {
            return "dev";
        }
        try (in) {
            Properties properties = new Properties();
            properties.load(in);
            String version = properties.getProperty("version", "dev");
            return version.startsWith("${") ? "dev" : version;
        } catch (IOException e) {
            return "dev";
        }
    }
}
