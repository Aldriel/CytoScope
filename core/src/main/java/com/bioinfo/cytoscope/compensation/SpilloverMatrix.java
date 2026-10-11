package com.bioinfo.cytoscope.compensation;

import com.bioinfo.cytoscope.fcs.FcsSample;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Spillover of each fluorochrome into the detectors of the others.
 *
 * <p>Row {@code i} describes the fluorochrome measured by channel {@code i}: its value in column
 * {@code j} is the fraction of its signal that the detector of channel {@code j} also receives.
 * The diagonal is 1. Compensation multiplies the measured values by the inverse of this matrix,
 * which removes the spillover.
 */
public final class SpilloverMatrix {

    private static final String[] KEYWORDS = {"$SPILLOVER", "SPILL", "SPILLOVER", "$COMP"};

    private final List<String> channels;
    private final double[][] values;

    /**
     * Creates a matrix.
     *
     * @param channels names of the compensated channels, in the order of the rows
     * @param values square matrix of spillover fractions, with 1 on the diagonal
     */
    public SpilloverMatrix(List<String> channels, double[][] values) {
        this.channels = List.copyOf(channels);
        if (channels.isEmpty() || values.length != channels.size()) {
            throw new IllegalArgumentException("The matrix needs one row per channel");
        }
        this.values = new double[values.length][];
        for (int i = 0; i < values.length; i++) {
            if (values[i].length != channels.size()) {
                throw new IllegalArgumentException("The spillover matrix must be square");
            }
            for (double value : values[i]) {
                if (!Double.isFinite(value)) {
                    throw new IllegalArgumentException("The spillover values must be numbers");
                }
            }
            this.values[i] = values[i].clone();
        }
        inverse();
    }

    /**
     * Returns the matrix that leaves the values unchanged.
     *
     * @param channels names of the channels
     * @return a matrix with 1 on the diagonal and 0 elsewhere
     */
    public static SpilloverMatrix identity(List<String> channels) {
        double[][] values = new double[channels.size()][channels.size()];
        for (int i = 0; i < values.length; i++) {
            values[i][i] = 1;
        }
        return new SpilloverMatrix(channels, values);
    }

    /**
     * Reads the matrix written by the cytometer software in the keywords of a file.
     *
     * @param sample the sample
     * @return the matrix, if the file has one that matches its channels
     */
    public static Optional<SpilloverMatrix> fromSample(FcsSample sample) {
        for (String keyword : KEYWORDS) {
            Optional<String> value = sample.keyword(keyword);
            if (value.isPresent()) {
                try {
                    SpilloverMatrix matrix = parse(value.get());
                    if (matrix.channels.stream().allMatch(c -> sample.indexOf(c) >= 0)) {
                        return Optional.of(matrix);
                    }
                } catch (IllegalArgumentException e) {
                    // An unreadable keyword is ignored: the file is used without compensation.
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Parses the FCS form of the matrix: the number of channels, their names, then the values
     * row by row, all separated by commas.
     *
     * @param text for example {@code 2,FITC-A,PI-A,1,0.1,0.02,1}
     * @return the matrix
     * @throws IllegalArgumentException if the text is not a valid matrix
     */
    public static SpilloverMatrix parse(String text) {
        String[] parts = text.split(",");
        int n;
        try {
            n = Integer.parseInt(parts[0].trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("The matrix must start with its size");
        }
        if (n <= 0 || parts.length != 1 + n + n * n) {
            throw new IllegalArgumentException("The matrix does not have " + n + " channels");
        }
        List<String> names = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            names.add(parts[1 + i].trim());
        }
        double[][] values = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                try {
                    values[i][j] = Double.parseDouble(parts[1 + n + i * n + j].trim());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid spillover value", e);
                }
            }
        }
        return new SpilloverMatrix(names, values);
    }

    /**
     * Returns the FCS form of the matrix, as written in the $SPILLOVER keyword.
     *
     * @return the matrix as text
     */
    public String format() {
        StringBuilder text = new StringBuilder().append(channels.size());
        channels.forEach(c -> text.append(',').append(c));
        for (double[] row : values) {
            for (double value : row) {
                text.append(',').append(String.format(Locale.ROOT, "%.6g", value));
            }
        }
        return text.toString();
    }

    /**
     * Returns the names of the compensated channels.
     *
     * @return read-only list, in the order of the rows
     */
    public List<String> channels() {
        return channels;
    }

    /**
     * Returns one spillover fraction.
     *
     * @param from row: the fluorochrome
     * @param to column: the detector that receives part of its signal
     * @return the fraction, 1 on the diagonal
     */
    public double value(int from, int to) {
        return values[from][to];
    }

    /**
     * Returns a copy of the matrix with one value changed, as when the user edits it.
     *
     * @param from row
     * @param to column
     * @param value the new fraction
     * @return the new matrix
     */
    public SpilloverMatrix with(int from, int to, double value) {
        double[][] copy = new double[values.length][];
        for (int i = 0; i < values.length; i++) {
            copy[i] = values[i].clone();
        }
        copy[from][to] = value;
        return new SpilloverMatrix(channels, copy);
    }

    /**
     * Returns the inverse of the matrix, by Gauss-Jordan elimination with partial pivoting.
     *
     * @return the compensation matrix
     * @throws IllegalArgumentException if the matrix cannot be inverted
     */
    public double[][] inverse() {
        int n = values.length;
        double[][] a = new double[n][2 * n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(values[i], 0, a[i], 0, n);
            a[i][n + i] = 1;
        }
        for (int col = 0; col < n; col++) {
            int pivot = col;
            for (int row = col + 1; row < n; row++) {
                if (Math.abs(a[row][col]) > Math.abs(a[pivot][col])) {
                    pivot = row;
                }
            }
            if (Math.abs(a[pivot][col]) < 1e-12) {
                throw new IllegalArgumentException(
                        "The spillover matrix cannot be inverted: check its values");
            }
            double[] swap = a[col];
            a[col] = a[pivot];
            a[pivot] = swap;
            double scale = a[col][col];
            for (int j = 0; j < 2 * n; j++) {
                a[col][j] /= scale;
            }
            for (int row = 0; row < n; row++) {
                if (row != col && a[row][col] != 0) {
                    double factor = a[row][col];
                    for (int j = 0; j < 2 * n; j++) {
                        a[row][j] -= factor * a[col][j];
                    }
                }
            }
        }
        double[][] inverse = new double[n][n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(a[i], n, inverse[i], 0, n);
        }
        return inverse;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof SpilloverMatrix that) || !channels.equals(that.channels)) {
            return false;
        }
        return Arrays.deepEquals(values, that.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(channels, Arrays.deepHashCode(values));
    }
}
