package com.bioinfo.cytoscope.server.billing;

/**
 * Converts the number of events in an FCS file into Metabolix credits. One credit is 100,000
 * events; each file is rounded up to the next credit.
 */
public final class CreditMeter {

    /** Events covered by one credit. */
    public static final int EVENTS_PER_CREDIT = 100_000;

    /** Free own-file uploads before an access code is required. */
    public static final int FREE_TRIAL_FILES = 5;

    private CreditMeter() {}

    /**
     * Returns how many credits an FCS file costs.
     *
     * @param eventCount number of events in the file; negative values are rejected
     * @return 0 when there is no event, otherwise {@code ceil(eventCount / 100_000)}
     * @throws IllegalArgumentException if {@code eventCount} is negative
     */
    public static int creditsFor(int eventCount) {
        if (eventCount < 0) {
            throw new IllegalArgumentException("eventCount must be >= 0");
        }
        if (eventCount == 0) {
            return 0;
        }
        return (eventCount + EVENTS_PER_CREDIT - 1) / EVENTS_PER_CREDIT;
    }
}
