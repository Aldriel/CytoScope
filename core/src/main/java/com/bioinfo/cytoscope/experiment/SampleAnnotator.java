package com.bioinfo.cytoscope.experiment;

import com.bioinfo.cytoscope.fcs.Channel;
import com.bioinfo.cytoscope.fcs.FcsSample;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Guesses the role, group and dose of a tube from its keywords and its file name, so that the
 * user only corrects what is wrong.
 *
 * <p>The keywords {@code CYTOSCOPE_GROUP}, {@code CYTOSCOPE_DOSE} and {@code CYTOSCOPE_ROLE},
 * written by CytoScope, come first. Otherwise the name is cleaned of the acquisition date, the
 * tube number and the replicate number: {@code 081120_Tube_004 TMZ 1} becomes the group
 * {@code TMZ}. A concentration such as {@code 2.5uM} gives the dose.
 */
public final class SampleAnnotator {

    /** Keyword of the treatment group. */
    public static final String GROUP_KEYWORD = "CYTOSCOPE_GROUP";
    /** Keyword of the dose. */
    public static final String DOSE_KEYWORD = "CYTOSCOPE_DOSE";
    /** Keyword of the role, with the dye channel after a colon for a single stain. */
    public static final String ROLE_KEYWORD = "CYTOSCOPE_ROLE";

    private static final Pattern DOSE = Pattern.compile(
            "(\\d+(?:[.,]\\d+)?)\\s*(pm|nm|um|µm|μm|mm|ng/ml|ug/ml|µg/ml|mg/kg|mg/l|%)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE = Pattern.compile("^\\d{6,8}[_ -]+");
    private static final Pattern TUBE = Pattern.compile(
            "^(?:specimen_\\d+_)?(?:tube[_ ]?\\d+[_ ]*|\\d{1,3}[_ ]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern REPLICATE = Pattern.compile(
            "(?:[_ -]*(?:rep|r|replicate)?[_ -]*\\d{1,2})$", Pattern.CASE_INSENSITIVE);

    private SampleAnnotator() {}

    /**
     * Guesses the information of a tube.
     *
     * @param id identifier of the loaded file
     * @param sample the sample; its name is the file name without extension
     * @return the guessed information
     */
    public static SampleInfo guess(String id, FcsSample sample) {
        String name = sample.name();
        double dose = sample.keyword(DOSE_KEYWORD).map(SampleAnnotator::parseNumber)
                .orElseGet(() -> parseDose(name));
        Optional<String> role = sample.keyword(ROLE_KEYWORD);
        if (role.isPresent()) {
            String[] parts = role.get().split(":", 2);
            SampleRole parsed = parseRole(parts[0]);
            String channel = parts.length > 1 ? parts[1] : "";
            String group = sample.keyword(GROUP_KEYWORD).orElse("");
            return new SampleInfo(id, name, parsed, parsed == SampleRole.SAMPLE ? group : "",
                    dose, parsed == SampleRole.SINGLE_STAIN ? channel : "");
        }
        String group = sample.keyword(GROUP_KEYWORD).orElseGet(() -> groupOf(name));
        String upper = group.toUpperCase(Locale.ROOT);
        if (upper.matches("UNSTAIN(ED)?|NON ?MARQU[EÉ]|BLANK|AUTOFLUO.*")) {
            return new SampleInfo(id, name, SampleRole.UNSTAINED, "", Double.NaN, "");
        }
        Optional<String> stain = singleStainChannel(sample, upper);
        if (stain.isPresent()) {
            return new SampleInfo(id, name, SampleRole.SINGLE_STAIN, "", Double.NaN,
                    stain.get());
        }
        return new SampleInfo(id, name, SampleRole.SAMPLE, group, dose, "");
    }

    /**
     * Cleans a file name into a group name.
     *
     * @param name the file name without extension
     * @return the group, in upper case
     */
    static String groupOf(String name) {
        String group = DATE.matcher(name.trim()).replaceFirst("");
        group = TUBE.matcher(group).replaceFirst("");
        group = REPLICATE.matcher(group).replaceFirst("");
        group = group.replaceAll("[_]+", " ").trim();
        return group.isEmpty() ? name.trim().toUpperCase(Locale.ROOT)
                : group.toUpperCase(Locale.ROOT);
    }

    /**
     * Reads a dose such as {@code 2.5uM} or {@code 10 ng/mL} in a name.
     *
     * @param name the name
     * @return the number, NaN if the name has none
     */
    static double parseDose(String name) {
        Matcher matcher = DOSE.matcher(name);
        return matcher.find() ? parseNumber(matcher.group(1)) : Double.NaN;
    }

    private static double parseNumber(String text) {
        try {
            double value = Double.parseDouble(text.trim().replace(',', '.'));
            return value >= 0 ? value : Double.NaN;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static SampleRole parseRole(String text) {
        try {
            return SampleRole.valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return SampleRole.SAMPLE;
        }
    }

    /**
     * Recognizes a single-stain control named after its dye or its channel, such as
     * {@code ANNEXIN}, {@code PI} or {@code FITC}.
     */
    private static Optional<String> singleStainChannel(FcsSample sample, String group) {
        String dye = group.replaceAll("\\s*(ONLY|SEUL(E)?|SINGLE|CONTROL|CTRL)$", "").trim();
        for (Channel channel : sample.channels()) {
            if (!channel.isFluorescence()) {
                continue;
            }
            String channelName = channel.name().toUpperCase(Locale.ROOT);
            String stem = channelName.replaceAll("-[AHW]$", "");
            String label = channel.label().toUpperCase(Locale.ROOT);
            if (dye.equals(stem) || dye.equals(channelName)
                    || (!label.isEmpty() && (label.contains(dye) || dye.contains(label)))) {
                return Optional.of(channel.name());
            }
        }
        if (dye.startsWith("ANNEXIN")) {
            return sample.channels().stream().filter(c -> c.name().toUpperCase(Locale.ROOT)
                    .startsWith("FITC")).map(Channel::name).findFirst();
        }
        return Optional.empty();
    }
}
