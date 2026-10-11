package com.bioinfo.cytoscope.analysis;

/**
 * The four states told apart by annexin V and propidium iodide (PI). Annexin V binds the
 * phosphatidylserine exposed on the outer membrane early in apoptosis; PI only enters cells
 * whose membrane is broken.
 */
public enum CellFate {
    /** Annexin V negative, PI negative: intact cells. */
    LIVE,
    /** Annexin V positive, PI negative: early apoptosis, membrane still intact. */
    EARLY_APOPTOTIC,
    /** Annexin V positive, PI positive: late apoptosis or secondary necrosis. */
    LATE_APOPTOTIC,
    /** Annexin V negative, PI positive: necrosis or damaged membranes. */
    NECROTIC;

    /**
     * Returns the state of an event from its two stains.
     *
     * @param annexinPositive true above the annexin V threshold
     * @param piPositive true above the PI threshold
     * @return the state
     */
    public static CellFate of(boolean annexinPositive, boolean piPositive) {
        if (annexinPositive) {
            return piPositive ? LATE_APOPTOTIC : EARLY_APOPTOTIC;
        }
        return piPositive ? NECROTIC : LIVE;
    }
}
