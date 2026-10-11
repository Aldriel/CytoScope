package com.bioinfo.cytoscope.gating;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The gating hierarchy applied to every sample of an experiment, as a template: the same gates
 * select the populations of each tube.
 *
 * <p>The tree is immutable. Each change returns a new tree, which makes undo, sharing between
 * threads and comparison with the previous state simple.
 */
public final class GatingTree {

    private final Population root;
    private final int nextId;

    private GatingTree(Population root, int nextId) {
        this.root = root;
        this.nextId = nextId;
    }

    /**
     * Creates a hierarchy that only holds the population of all events.
     *
     * @param rootName name of the population of all events
     * @return the new tree
     */
    public static GatingTree create(String rootName) {
        return new GatingTree(Population.root(rootName), 1);
    }

    /**
     * Rebuilds a tree from its root, for example after reading it from a file or a request.
     *
     * @param root the root population
     * @return the tree
     * @throws IllegalArgumentException if the root has a gate or two populations share an id
     */
    public static GatingTree of(Population root) {
        Objects.requireNonNull(root, "root");
        if (root.gate() != null || !root.id().equals(Population.ROOT_ID)) {
            throw new IllegalArgumentException("The root holds all the events: it has no gate");
        }
        List<Population> all = root.flatten();
        if (all.stream().map(Population::id).distinct().count() != all.size()) {
            throw new IllegalArgumentException("Two populations have the same identifier");
        }
        for (Population population : all.subList(1, all.size())) {
            if (population.gate() == null) {
                throw new IllegalArgumentException(
                        "The population " + population.name() + " has no gate");
            }
        }
        int next = 1;
        for (Population population : all) {
            if (population.id().matches("p\\d+")) {
                next = Math.max(next, Integer.parseInt(population.id().substring(1)) + 1);
            }
        }
        return new GatingTree(root, next);
    }

    /**
     * Returns the population of all events, root of the hierarchy.
     *
     * @return the root
     */
    public Population root() {
        return root;
    }

    /**
     * Finds a population.
     *
     * @param id identifier of the population
     * @return the population, if the tree has it
     */
    public Optional<Population> find(String id) {
        return root.find(id);
    }

    /**
     * Returns the identifier the next added population will receive.
     *
     * @return for example {@code p3}
     */
    public String nextId() {
        return "p" + nextId;
    }

    /**
     * Adds a population under another one.
     *
     * @param parentId identifier of the parent
     * @param name name of the new population
     * @param gate region that selects its events in the parent
     * @return the new tree; its {@link #nextId()} has moved to the following identifier
     * @throws IllegalArgumentException if the parent does not exist
     */
    public GatingTree add(String parentId, String name, Gate gate) {
        requirePopulation(parentId);
        Population child = Population.leaf(nextId(), name, gate);
        return new GatingTree(root.update(parentId, parent -> parent.withChild(child)),
                nextId + 1);
    }

    /**
     * Adds the four populations of a quadrant gate under a population.
     *
     * @param parentId identifier of the parent
     * @param names names of the four regions, in the order of {@link Quadrant#values()}
     * @param xChannel channel of the horizontal axis
     * @param yChannel channel of the vertical axis
     * @param xThreshold horizontal threshold
     * @param yThreshold vertical threshold
     * @return the new tree
     */
    public GatingTree addQuadrants(String parentId, List<String> names, String xChannel,
                                   String yChannel, double xThreshold, double yThreshold) {
        if (names.size() != Quadrant.values().length) {
            throw new IllegalArgumentException("Four names are needed, one per quadrant");
        }
        GatingTree tree = this;
        for (Quadrant quadrant : Quadrant.values()) {
            tree = tree.add(parentId, names.get(quadrant.ordinal()),
                    new QuadrantGate(xChannel, yChannel, xThreshold, yThreshold, quadrant));
        }
        return tree;
    }

    /**
     * Removes a population and the populations gated from it.
     *
     * @param id identifier of the population; not the root
     * @return the new tree
     */
    public GatingTree remove(String id) {
        requirePopulation(id);
        if (id.equals(Population.ROOT_ID)) {
            throw new IllegalArgumentException("The population of all events cannot be removed");
        }
        return new GatingTree(root.remove(id), nextId);
    }

    /**
     * Renames a population.
     *
     * @param id identifier of the population
     * @param name new name
     * @return the new tree
     */
    public GatingTree rename(String id, String name) {
        requirePopulation(id);
        return new GatingTree(root.update(id, p -> p.withName(name)), nextId);
    }

    /**
     * Replaces the gate of a population, as when the user moves or reshapes it. Moving one
     * quadrant moves its three siblings too, since they share the thresholds.
     *
     * @param id identifier of the population; not the root
     * @param gate the new gate
     * @return the new tree
     */
    public GatingTree moveGate(String id, Gate gate) {
        Population population = requirePopulation(id);
        if (population.gate() == null) {
            throw new IllegalArgumentException("The population of all events has no gate");
        }
        Population updated = root.update(id, p -> p.withGate(gate));
        if (population.gate() instanceof QuadrantGate old && gate instanceof QuadrantGate moved) {
            Population parent = root.parentOf(id).orElseThrow();
            for (Population sibling : parent.children()) {
                if (sibling.gate() instanceof QuadrantGate other && !sibling.id().equals(id)
                        && other.xChannel().equals(old.xChannel())
                        && other.yChannel().equals(old.yChannel())
                        && other.xThreshold() == old.xThreshold()
                        && other.yThreshold() == old.yThreshold()) {
                    updated = updated.update(sibling.id(), p -> p.withGate(
                            other.withThresholds(moved.xThreshold(), moved.yThreshold())));
                }
            }
        }
        return new GatingTree(updated, nextId);
    }

    private Population requirePopulation(String id) {
        return root.find(id).orElseThrow(
                () -> new IllegalArgumentException("Unknown population: " + id));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof GatingTree that && root.equals(that.root);
    }

    @Override
    public int hashCode() {
        return root.hashCode();
    }
}
