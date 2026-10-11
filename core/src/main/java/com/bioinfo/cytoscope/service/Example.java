package com.bioinfo.cytoscope.service;

/** The example experiments shipped with CytoScope. */
public enum Example {

    /** Annexin V / PI test of U-87MG glioblastoma cells after five treatments (real data). */
    U87_APOPTOSIS("u87-apoptosis", false),
    /** DNA content of U-87MG glioblastoma cells after five treatments (real data). */
    U87_CELL_CYCLE("u87-cell-cycle", false),
    /** Annexin V / PI test of splenocytes along a cadmium dose series (simulated data). */
    CADMIUM("cadmium", true);

    private final String folder;
    private final boolean simulated;

    Example(String folder, boolean simulated) {
        this.folder = folder;
        this.simulated = simulated;
    }

    /**
     * Returns the folder of the files, which also identifies the example in the web interface.
     *
     * @return the folder name
     */
    public String folder() {
        return folder;
    }

    /**
     * Tells whether the data were invented rather than measured.
     *
     * @return true for simulated data
     */
    public boolean simulated() {
        return simulated;
    }

    /**
     * Finds an example by its folder.
     *
     * @param folder the folder name
     * @return the example
     * @throws IllegalArgumentException if there is no such example
     */
    public static Example fromFolder(String folder) {
        for (Example example : values()) {
            if (example.folder.equals(folder)) {
                return example;
            }
        }
        throw new IllegalArgumentException("Unknown example: " + folder);
    }
}
