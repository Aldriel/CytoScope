package com.bioinfo.cytoscope.experiment;

import java.util.Objects;

/**
 * What the user tells about a tube: its role, its group and its dose.
 *
 * @param id identifier of the loaded file, unique in the experiment
 * @param name name shown to the user
 * @param role part the tube plays
 * @param group treatment group, such as {@code NC} or {@code TMZ}; empty for controls
 * @param dose dose or concentration received, NaN if not given
 * @param stainChannel channel of the dye of a single-stain control; empty otherwise
 */
public record SampleInfo(String id, String name, SampleRole role, String group, double dose,
                         String stainChannel) {

    /** Creates the information and normalizes the empty fields. */
    public SampleInfo {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(role, "role");
        group = group == null ? "" : group.trim();
        stainChannel = stainChannel == null ? "" : stainChannel.trim();
        if (dose < 0) {
            throw new IllegalArgumentException("A dose cannot be negative");
        }
    }

    /**
     * Tells whether the tube is a sample with a group, which takes part in the comparisons.
     *
     * @return true for samples with a group
     */
    public boolean isCompared() {
        return role == SampleRole.SAMPLE && !group.isEmpty();
    }

    /**
     * Returns a copy with another role.
     *
     * @param newRole the role
     * @param channel the dye channel for a single stain, empty otherwise
     * @return the new information
     */
    public SampleInfo withRole(SampleRole newRole, String channel) {
        return new SampleInfo(id, name, newRole, group, dose,
                newRole == SampleRole.SINGLE_STAIN ? channel : "");
    }

    /**
     * Returns a copy with another group.
     *
     * @param newGroup the group
     * @return the new information
     */
    public SampleInfo withGroup(String newGroup) {
        return new SampleInfo(id, name, role, newGroup, dose, stainChannel);
    }

    /**
     * Returns a copy with another dose.
     *
     * @param newDose the dose, NaN for none
     * @return the new information
     */
    public SampleInfo withDose(double newDose) {
        return new SampleInfo(id, name, role, group, newDose, stainChannel);
    }
}
