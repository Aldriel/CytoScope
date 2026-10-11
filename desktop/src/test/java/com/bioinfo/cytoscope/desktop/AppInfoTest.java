package com.bioinfo.cytoscope.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

class AppInfoTest {

    private static InputStream text(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.ISO_8859_1));
    }

    @Test
    void versionComesFromTheMavenBuild() {
        assertTrue(AppInfo.version().matches("\\d+\\.\\d+\\.\\d+.*"), AppInfo.version());
    }

    @Test
    void missingOrUnfilteredVersionsFallBackToDev() {
        assertEquals("1.2.3", AppInfo.readVersion(text("version=1.2.3")));
        assertEquals("dev", AppInfo.readVersion(null));
        assertEquals("dev", AppInfo.readVersion(text("version=${project.version}")));
        assertEquals("dev", AppInfo.readVersion(text("other=1")));
        assertEquals(
                "dev",
                AppInfo.readVersion(
                        new InputStream() {
                            @Override
                            public int read() throws IOException {
                                throw new IOException("broken");
                            }
                        }));
    }
}
