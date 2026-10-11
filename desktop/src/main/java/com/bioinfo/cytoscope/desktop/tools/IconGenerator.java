package com.bioinfo.cytoscope.desktop.tools;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.StrokeLineCap;
import javafx.stage.Stage;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.imageio.ImageIO;

/**
 * Draws the application icon (a dot plot: two clouds of cells, the amber one inside a gate) as PNG
 * files for the window, as a Windows {@code .ico} for the executable and as the favicon of the web
 * application. Run with {@code mvnw -pl desktop javafx:run@icons}.
 */
public final class IconGenerator extends Application {

    private static final int[] SIZES = {16, 32, 48, 64, 256};

    /**
     * Starts the generator.
     *
     * @param args unused
     */
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws IOException {
        System.setProperty("java.awt.headless", "true");
        Path module = Files.isDirectory(Path.of("desktop")) ? Path.of("desktop") : Path.of(".");
        Path view = module.resolve("src/main/resources/com/bioinfo/cytoscope/desktop/view");
        Path packaging = module.resolve("packaging");
        Files.createDirectories(packaging);

        List<byte[]> pngs = new ArrayList<>();
        for (int size : SIZES) {
            byte[] png = png(draw(size));
            Files.write(view.resolve("icon-" + size + ".png"), png);
            pngs.add(png);
        }
        Files.write(packaging.resolve("CytoScope.ico"), ico(pngs));
        Path web = module.resolve("../web/public").normalize();
        Files.createDirectories(web);
        Files.write(web.resolve("favicon.png"), png(draw(64)));
        System.out.println(
                "Icons written to " + view.toAbsolutePath().normalize() + " and " + packaging);
        Platform.exit();
    }

    private static WritableImage draw(int size) {
        Canvas canvas = new Canvas(size, size);
        GraphicsContext g = canvas.getGraphicsContext2D();
        double arc = size * 0.22;
        g.setFill(
                new LinearGradient(
                        0,
                        0,
                        0,
                        1,
                        true,
                        CycleMethod.NO_CYCLE,
                        new Stop(0, Color.web("#14a395")),
                        new Stop(1, Color.web("#0b4f4a"))));
        g.fillRoundRect(0, 0, size, size, arc, arc);

        double margin = size * 0.18;
        g.setLineCap(StrokeLineCap.ROUND);
        g.setStroke(Color.web("#ffffff", 0.85));
        g.setLineWidth(Math.max(1, size * 0.045));
        g.strokeLine(margin, margin * 0.8, margin, size - margin);
        g.strokeLine(margin, size - margin, size - margin * 0.8, size - margin);

        Random random = new Random(7);
        double dot = Math.max(1.2, size * 0.055);
        int count = size < 32 ? 7 : 18;
        g.setFill(Color.web("#ffffff", 0.9));
        cluster(g, random, size * 0.36, size * 0.62, size * 0.09, dot, count);
        g.setFill(Color.web("#fbbf24"));
        cluster(g, random, size * 0.64, size * 0.36, size * 0.08, dot, count);

        double gate = size * 0.21;
        g.setStroke(Color.web("#fbbf24"));
        g.setLineWidth(Math.max(1, size * 0.05));
        g.strokeRoundRect(
                size * 0.64 - gate, size * 0.36 - gate, 2 * gate, 2 * gate, gate, gate);
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        return canvas.snapshot(params, null);
    }

    /** Draws a cloud of events around a centre, as on a dot plot. */
    private static void cluster(
            GraphicsContext g,
            Random random,
            double cx,
            double cy,
            double spread,
            double dot,
            int count) {
        for (int i = 0; i < count; i++) {
            double x = cx + random.nextGaussian() * spread;
            double y = cy + random.nextGaussian() * spread;
            g.fillOval(x - dot / 2, y - dot / 2, dot, dot);
        }
    }

    private static byte[] png(WritableImage image) throws IOException {
        int w = (int) image.getWidth();
        int h = (int) image.getHeight();
        int[] pixels = new int[w * h];
        image.getPixelReader()
                .getPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), pixels, 0, w);
        BufferedImage buffered = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        buffered.setRGB(0, 0, w, h, pixels, 0, w);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(buffered, "png", out);
        return out.toByteArray();
    }

    /**
     * Packs PNG images into an ICO file (PNG-compressed entries, supported since Windows Vista).
     */
    private static byte[] ico(List<byte[]> pngs) {
        int headerSize = 6 + 16 * pngs.size();
        int total = headerSize + pngs.stream().mapToInt(p -> p.length).sum();
        ByteBuffer buffer = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putShort((short) 0).putShort((short) 1).putShort((short) pngs.size());
        int offset = headerSize;
        for (int i = 0; i < pngs.size(); i++) {
            int size = SIZES[i];
            buffer.put((byte) (size >= 256 ? 0 : size)).put((byte) (size >= 256 ? 0 : size));
            buffer.put((byte) 0).put((byte) 0);
            buffer.putShort((short) 1).putShort((short) 32);
            buffer.putInt(pngs.get(i).length).putInt(offset);
            offset += pngs.get(i).length;
        }
        pngs.forEach(buffer::put);
        return buffer.array();
    }
}
