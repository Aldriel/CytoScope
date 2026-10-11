package com.bioinfo.cytoscope.gating;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * A node of the gating hierarchy: the events of its parent that fall inside its gate, and the
 * populations gated from it (Composite pattern). The root has no gate: it holds all the events.
 *
 * <p>Populations are immutable; the methods that change the tree return a new tree.
 *
 * @param id identifier, unique in the tree and stable when the population is renamed
 * @param name name shown to the user, such as {@code Cells} or {@code Annexin+ / PI-}
 * @param gate region that selects the events in the parent; null for the root
 * @param children populations gated from this one
 */
public record Population(String id, String name, Gate gate, List<Population> children) {

    /** Identifier of the root population, which holds all the events. */
    public static final String ROOT_ID = "all";

    /** Creates a population and copies its children. */
    public Population {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        if (id.isBlank() || name.isBlank()) {
            throw new IllegalArgumentException("A population needs an identifier and a name");
        }
        children = List.copyOf(children);
    }

    /**
     * Creates a population without children.
     *
     * @param id identifier
     * @param name name shown to the user
     * @param gate region that selects the events in the parent
     * @return the population
     */
    public static Population leaf(String id, String name, Gate gate) {
        return new Population(id, name, Objects.requireNonNull(gate, "gate"), List.of());
    }

    /**
     * Creates the root of a new hierarchy.
     *
     * @param name name of the population of all events, translated by the caller
     * @return the root, without gate or children
     */
    public static Population root(String name) {
        return new Population(ROOT_ID, name, null, List.of());
    }

    /**
     * Finds a population in this subtree.
     *
     * @param populationId identifier of the population
     * @return the population, if it is this one or one of its descendants
     */
    public Optional<Population> find(String populationId) {
        if (id.equals(populationId)) {
            return Optional.of(this);
        }
        for (Population child : children) {
            Optional<Population> found = child.find(populationId);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    /**
     * Finds the parent of a population in this subtree.
     *
     * @param populationId identifier of the child
     * @return the parent, if the population is a descendant of this one
     */
    public Optional<Population> parentOf(String populationId) {
        for (Population child : children) {
            if (child.id.equals(populationId)) {
                return Optional.of(this);
            }
            Optional<Population> found = child.parentOf(populationId);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    /**
     * Returns this population and all its descendants, parents before children.
     *
     * @return the populations in depth-first order
     */
    public List<Population> flatten() {
        List<Population> all = new ArrayList<>();
        collect(this, all);
        return all;
    }

    private static void collect(Population population, List<Population> all) {
        all.add(population);
        population.children.forEach(child -> collect(child, all));
    }

    /**
     * Returns the tree with one population replaced.
     *
     * @param populationId identifier of the population to replace
     * @param change function that returns the new version of the population
     * @return the new tree; unchanged if the population is not found
     */
    public Population update(String populationId, UnaryOperator<Population> change) {
        if (id.equals(populationId)) {
            return change.apply(this);
        }
        List<Population> updated = new ArrayList<>(children.size());
        for (Population child : children) {
            updated.add(child.update(populationId, change));
        }
        return new Population(id, name, gate, updated);
    }

    /**
     * Returns the tree with a population removed, together with its descendants.
     *
     * @param populationId identifier of the population to remove; not the root
     * @return the new tree
     */
    public Population remove(String populationId) {
        List<Population> kept = new ArrayList<>(children.size());
        for (Population child : children) {
            if (!child.id.equals(populationId)) {
                kept.add(child.remove(populationId));
            }
        }
        return new Population(id, name, gate, kept);
    }

    /**
     * Returns a copy of this population with one more child.
     *
     * @param child the new child
     * @return the new population
     */
    public Population withChild(Population child) {
        List<Population> more = new ArrayList<>(children);
        more.add(child);
        return new Population(id, name, gate, more);
    }

    /**
     * Returns a copy of this population with another name.
     *
     * @param newName the new name
     * @return the renamed population
     */
    public Population withName(String newName) {
        return new Population(id, newName, gate, children);
    }

    /**
     * Returns a copy of this population with another gate.
     *
     * @param newGate the new gate
     * @return the population with its gate moved or reshaped
     */
    public Population withGate(Gate newGate) {
        return new Population(id, name, newGate, children);
    }
}
