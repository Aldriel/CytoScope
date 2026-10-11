package com.bioinfo.cytoscope.analysis;

/**
 * Regions of a DNA content histogram, from the least to the most DNA.
 */
public enum CellCyclePhase {
    /** Less DNA than a resting cell: fragmented DNA of apoptotic cells, and debris. */
    SUB_G1,
    /** One copy of the genome (2N): resting or growing cells. */
    G0_G1,
    /** Between 2N and 4N: DNA being replicated. */
    S,
    /** Two copies of the genome (4N): before or during division. */
    G2_M,
    /** More than 4N: polyploid cells or aggregates left after doublet exclusion. */
    ABOVE_G2
}
