package com.bioinfo.cytoscope.desktop.view;

import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;

import java.awt.image.BufferedImage;

/** Converts the charts drawn by the core library (AWT images) to JavaFX images. */
public final class Images {

    private Images() {}

    /**
     * Copies the pixels of an AWT image.
     *
     * @param picture the image
     * @return the same pixels as a JavaFX image
     */
    public static Image toFx(BufferedImage picture) {
        int width = picture.getWidth();
        int height = picture.getHeight();
        int[] pixels = picture.getRGB(0, 0, width, height, null, 0, width);
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] |= 0xff000000;
        }
        WritableImage image = new WritableImage(width, height);
        image.getPixelWriter()
                .setPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width);
        return image;
    }
}
