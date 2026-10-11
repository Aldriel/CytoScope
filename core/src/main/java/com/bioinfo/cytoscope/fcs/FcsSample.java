package com.bioinfo.cytoscope.fcs;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The events of one tube: the values measured for every cell, channel by channel.
 *
 * <p>The values are stored by channel (one array per channel) because the analyses read a few
 * channels over all the events. The sample is immutable: transformations such as compensation
 * return a new sample.
 */
public final class FcsSample {

    private final String name;
    private final Map<String, String> keywords;
    private final List<Channel> channels;
    private final float[][] values;

    /**
     * Creates a sample.
     *
     * @param name name shown to the user, usually the file name without extension
     * @param keywords keywords of the TEXT segment, with upper-case names
     * @param channels measured parameters, in the order of the file
     * @param values one array per channel, all of the same length; kept, not copied
     */
    public FcsSample(
            String name, Map<String, String> keywords, List<Channel> channels, float[][] values) {
        this.name = Objects.requireNonNull(name, "name");
        this.keywords = Collections.unmodifiableMap(new LinkedHashMap<>(keywords));
        this.channels = List.copyOf(channels);
        if (values.length != channels.size()) {
            throw new IllegalArgumentException("One array of values is needed per channel");
        }
        for (float[] column : values) {
            if (column.length != values[0].length) {
                throw new IllegalArgumentException("Every channel needs the same number of events");
            }
        }
        this.values = values;
    }

    /**
     * Returns the name shown to the user.
     *
     * @return the sample name
     */
    public String name() {
        return name;
    }

    /**
     * Returns a copy of this sample under another name.
     *
     * @param newName the new name
     * @return the renamed sample, sharing the same values
     */
    public FcsSample withName(String newName) {
        return new FcsSample(newName, keywords, channels, values);
    }

    /**
     * Returns the keywords of the TEXT segment.
     *
     * @return read-only map with upper-case keyword names
     */
    public Map<String, String> keywords() {
        return keywords;
    }

    /**
     * Returns the value of a keyword.
     *
     * @param keyword name of the keyword, in any case
     * @return the value, if the file has this keyword
     */
    public Optional<String> keyword(String keyword) {
        return Optional.ofNullable(keywords.get(keyword.toUpperCase(Locale.ROOT)));
    }

    /**
     * Returns the measured parameters.
     *
     * @return read-only list, in the order of the file
     */
    public List<Channel> channels() {
        return channels;
    }

    /**
     * Returns the number of events (cells or particles).
     *
     * @return the number of events
     */
    public int eventCount() {
        return values.length == 0 ? 0 : values[0].length;
    }

    /**
     * Finds a channel by its name or its stain label.
     *
     * @param nameOrLabel for example {@code PI-A} or {@code Annexin V}, ignoring case
     * @return the index of the channel, or -1 if there is none
     */
    public int indexOf(String nameOrLabel) {
        for (int i = 0; i < channels.size(); i++) {
            if (channels.get(i).name().equalsIgnoreCase(nameOrLabel)) {
                return i;
            }
        }
        for (int i = 0; i < channels.size(); i++) {
            if (channels.get(i).label().equalsIgnoreCase(nameOrLabel)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Finds a channel that must exist.
     *
     * @param nameOrLabel name or stain label of the channel
     * @return the index of the channel
     * @throws IllegalArgumentException if the sample has no such channel
     */
    public int requireChannel(String nameOrLabel) {
        int index = indexOf(nameOrLabel);
        if (index < 0) {
            throw new IllegalArgumentException(
                    "The sample " + name + " has no channel " + nameOrLabel);
        }
        return index;
    }

    /**
     * Returns one measured value.
     *
     * @param channel index of the channel
     * @param event index of the event
     * @return the value
     */
    public float value(int channel, int event) {
        return values[channel][event];
    }

    /**
     * Returns a copy of all the values of one channel.
     *
     * @param channel index of the channel
     * @return one value per event
     */
    public float[] column(int channel) {
        return values[channel].clone();
    }

    /**
     * Returns a sample with the same keywords and channels but other values.
     *
     * @param newValues one array per channel; kept, not copied
     * @return the new sample
     */
    public FcsSample withValues(float[][] newValues) {
        return new FcsSample(name, keywords, channels, newValues);
    }

    /**
     * Returns the values of every channel, for the classes of this package that write or
     * transform whole samples.
     *
     * @return the internal arrays, which must not be modified
     */
    float[][] rawValues() {
        return values;
    }
}
