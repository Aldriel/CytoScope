package com.bioinfo.cytoscope.experiment;

/**
 * Counts of one population in one tube.
 *
 * @param populationId identifier of the population
 * @param count number of events
 * @param percentOfParent share of the parent events, in percent
 * @param percentOfTotal share of all the events, in percent
 */
public record PopulationStats(String populationId, int count, double percentOfParent,
                              double percentOfTotal) {}
