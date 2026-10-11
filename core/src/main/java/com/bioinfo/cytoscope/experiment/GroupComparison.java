package com.bioinfo.cytoscope.experiment;

import com.bioinfo.cytoscope.stats.Descriptive;
import com.bioinfo.cytoscope.stats.Dunnett;
import com.bioinfo.cytoscope.stats.OneWayAnova;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Compares the groups of an experiment with the control group: one-way analysis of variance,
 * then Dunnett's test for each treated group.
 *
 * @param controlGroup name of the control group
 * @param groups summary of each group, the control first
 * @param anova the analysis of variance over all the groups
 */
public record GroupComparison(String controlGroup, List<GroupSummary> groups, OneWayAnova anova) {

    /**
     * Summary of one group and, for a treated group, its comparison with the control.
     *
     * @param name the group
     * @param values the value of each replicate
     * @param mean mean of the replicates
     * @param standardDeviation standard deviation of the replicates
     * @param comparison Dunnett's comparison with the control; null for the control itself
     */
    public record GroupSummary(String name, double[] values, double mean,
                               double standardDeviation, Dunnett.Comparison comparison) {

        /** Creates the summary and copies the values. */
        public GroupSummary {
            values = values.clone();
        }

        @Override
        public double[] values() {
            return values.clone();
        }

        /**
         * Returns the stars shown on charts: *** below 0.001, ** below 0.01, * below 0.05.
         *
         * @return the stars, or an empty text if the difference is not significant
         */
        public String stars() {
            if (comparison == null) {
                return "";
            }
            double p = comparison.adjustedP();
            return p < 0.001 ? "***" : p < 0.01 ? "**" : p < 0.05 ? "*" : "";
        }
    }

    /** Creates the comparison and copies the groups. */
    public GroupComparison {
        Objects.requireNonNull(controlGroup, "controlGroup");
        groups = List.copyOf(groups);
    }

    /**
     * Runs the comparison.
     *
     * @param valuesByGroup values of the replicates of each group, in display order
     * @param controlGroup name of the control group
     * @return the comparison
     * @throws IllegalArgumentException if the control group is missing, if there is no other
     *     group, or if no degree of freedom is left for the variance
     */
    public static GroupComparison of(Map<String, double[]> valuesByGroup, String controlGroup) {
        double[] control = valuesByGroup.get(controlGroup);
        if (control == null) {
            throw new IllegalArgumentException("The control group " + controlGroup
                    + " has no sample");
        }
        Map<String, double[]> treated = new LinkedHashMap<>(valuesByGroup);
        treated.remove(controlGroup);
        if (treated.isEmpty()) {
            throw new IllegalArgumentException("At least one group is needed besides the control");
        }
        List<double[]> all = new ArrayList<>();
        all.add(control);
        all.addAll(treated.values());
        OneWayAnova anova = OneWayAnova.of(all);
        List<Dunnett.Comparison> comparisons =
                Dunnett.compare(control, new ArrayList<>(treated.values()));

        List<GroupSummary> groups = new ArrayList<>();
        groups.add(summary(controlGroup, control, null));
        int i = 0;
        for (Map.Entry<String, double[]> entry : treated.entrySet()) {
            groups.add(summary(entry.getKey(), entry.getValue(), comparisons.get(i++)));
        }
        return new GroupComparison(controlGroup, groups, anova);
    }

    private static GroupSummary summary(String name, double[] values,
                                        Dunnett.Comparison comparison) {
        return new GroupSummary(name, values, Descriptive.mean(values),
                Descriptive.standardDeviation(values), comparison);
    }
}
