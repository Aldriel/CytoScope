package com.bioinfo.cytoscope.fcs;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Removes the FCS keywords that can name a person, a patient or an institution.
 *
 * <p>The measurements, the parameter descriptions and the spillover matrix stay. Keywords
 * written by CytoScope ({@code CYTOSCOPE_*}) stay as well. The file name chosen by the user
 * is not changed: that name is what the screen and the report show, so it should be a sample
 * code.
 */
public final class FcsPrivacy {

    /**
     * Standard FCS keywords whose value is an identity or a free-text description of one.
     * Acquisition time and instrument serial number are included because they can single out
     * one run.
     */
    private static final Set<String> DROPPED = Set.of(
            "$SRC", "$FIL", "$OP", "$INST", "$SYS", "$EXP", "$COM", "$CELLS", "$SMNO",
            "$CYTSN", "$DATE", "$BTIM", "$ETIM", "$GUID", "$PLATEID", "$PLATENAME", "$WELLID",
            "$LAST_MODIFIER", "$LAST_MODIFIED", "$ORIGINALITY", "$PROJ");

    /** Vendor keywords whose name, rather than the FCS standard, carries an identity. */
    private static final Pattern IDENTITY_NAME = Pattern.compile(
            "PATIENT|SUBJECT|DONOR|MRN|SSN|DOB|BIRTH|PHYSICIAN|OPERATOR|HOSPITAL|CLINIC"
                    + "|INSTITUT|PERSON|OWNER|E-?MAIL|PHONE|\\bNAME\\b|SPECIMEN|SAMPLE ID|CASE");

    private FcsPrivacy() {}

    /**
     * Returns a copy of the keywords without those that can identify a person or a site.
     *
     * @param keywords TEXT keywords, with upper-case names
     * @return the keywords safe to keep with the events
     */
    public static Map<String, String> redact(Map<String, String> keywords) {
        Map<String, String> kept = new LinkedHashMap<>(keywords);
        Iterator<String> names = kept.keySet().iterator();
        while (names.hasNext()) {
            String name = names.next();
            if (dropped(name)) {
                names.remove();
            }
        }
        return kept;
    }

    private static boolean dropped(String name) {
        if (name.startsWith("CYTOSCOPE_")) {
            return false;
        }
        if (DROPPED.contains(name)) {
            return true;
        }
        return IDENTITY_NAME.matcher(name.toUpperCase(Locale.ROOT)).find();
    }
}
