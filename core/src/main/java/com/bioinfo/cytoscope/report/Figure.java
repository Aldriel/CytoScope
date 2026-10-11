package com.bioinfo.cytoscope.report;

import java.awt.image.BufferedImage;
import java.util.Objects;

/**
 * A figure of the report.
 *
 * @param caption text below the picture
 * @param image the picture, drawn by {@link com.bioinfo.cytoscope.plot.Charts}
 */
public record Figure(String caption, BufferedImage image) {

    /** Creates the figure. */
    public Figure {
        Objects.requireNonNull(caption, "caption");
        Objects.requireNonNull(image, "image");
    }
}
