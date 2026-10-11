/**
 * Changes of the gating hierarchy, done in the browser before the experiment is sent to the
 * server. Like the Java GatingTree, every function returns a new tree and leaves the old one
 * unchanged.
 */
import { QUADRANTS, type Gate, type Population } from './types';

/** Identifier of the root, which holds all the events. */
export const ROOT_ID = 'all';

/** A population with its depth in the tree, for indented lists. */
export interface FlatPopulation {
  population: Population;
  depth: number;
  parentId: string | null;
}

/** Lists the populations, parents before children. */
export function flatten(root: Population): FlatPopulation[] {
  const all: FlatPopulation[] = [];
  const visit = (population: Population, depth: number, parentId: string | null) => {
    all.push({ population, depth, parentId });
    population.children.forEach((child) => visit(child, depth + 1, population.id));
  };
  visit(root, 0, null);
  return all;
}

/** Finds a population. */
export function find(root: Population, id: string): Population | null {
  return flatten(root).find((p) => p.population.id === id)?.population ?? null;
}

/** Finds the parent of a population. */
export function parentOf(root: Population, id: string): Population | null {
  const parentId = flatten(root).find((p) => p.population.id === id)?.parentId ?? null;
  return parentId === null ? null : find(root, parentId);
}

/** Returns the next free identifier, such as "p4", as the Java tree does. */
export function nextId(root: Population): string {
  let next = 1;
  for (const { population } of flatten(root)) {
    const match = /^p(\d+)$/.exec(population.id);
    if (match) {
      next = Math.max(next, Number(match[1]) + 1);
    }
  }
  return `p${next}`;
}

/** Replaces one population of the tree. */
function update(
  root: Population,
  id: string,
  change: (population: Population) => Population,
): Population {
  if (root.id === id) {
    return change(root);
  }
  return { ...root, children: root.children.map((child) => update(child, id, change)) };
}

/** Adds a population under another one. */
export function add(root: Population, parentId: string, name: string, gate: Gate): Population {
  const child: Population = { id: nextId(root), name, gate, children: [] };
  return update(root, parentId, (parent) => ({
    ...parent,
    children: [...parent.children, child],
  }));
}

/**
 * Adds the four regions of a quadrant gate under a population.
 *
 * @param names names of the regions, in the order of QUADRANTS
 */
export function addQuadrants(
  root: Population,
  parentId: string,
  names: readonly string[],
  xChannel: string,
  yChannel: string,
  xThreshold: number,
  yThreshold: number,
): Population {
  let tree = root;
  QUADRANTS.forEach((quadrant, i) => {
    tree = add(tree, parentId, names[i], {
      type: 'quadrant',
      xChannel,
      yChannel,
      xThreshold,
      yThreshold,
      quadrant,
    });
  });
  return tree;
}

/** Removes a population and its descendants; the root cannot be removed. */
export function remove(root: Population, id: string): Population {
  return {
    ...root,
    children: root.children.filter((child) => child.id !== id).map((c) => remove(c, id)),
  };
}

/** Renames a population. */
export function rename(root: Population, id: string, name: string): Population {
  return update(root, id, (population) => ({ ...population, name }));
}

/**
 * Moves or reshapes the gate of a population. A quadrant region moves its three siblings of the
 * same cross with it, so that the four regions stay complementary.
 */
export function moveGate(root: Population, id: string, gate: Gate): Population {
  const old = find(root, id)?.gate;
  let tree = update(root, id, (population) => ({ ...population, gate }));
  const parent = parentOf(root, id);
  if (old?.type === 'quadrant' && gate.type === 'quadrant' && parent) {
    for (const sibling of parent.children) {
      const g = sibling.gate;
      if (
        sibling.id !== id &&
        g?.type === 'quadrant' &&
        g.xChannel === old.xChannel &&
        g.yChannel === old.yChannel &&
        g.xThreshold === old.xThreshold &&
        g.yThreshold === old.yThreshold
      ) {
        tree = update(tree, sibling.id, (s) => ({
          ...s,
          gate: { ...g, xThreshold: gate.xThreshold, yThreshold: gate.yThreshold },
        }));
      }
    }
  }
  return tree;
}

/** Channels a gate is drawn on: its horizontal channel and, except for an interval, vertical. */
export function gateChannels(gate: Gate): [string, string | null] {
  return [gate.xChannel, gate.type === 'interval' ? null : gate.yChannel];
}

/**
 * Children of a population whose gate can be drawn on a plot of two channels (or of one channel
 * for a histogram, y null).
 */
export function drawableChildren(parent: Population, x: string, y: string | null): Population[] {
  return parent.children.filter((child) => {
    const gate = child.gate;
    if (gate === null) {
      return false;
    }
    if (gate.type === 'interval') {
      return gate.xChannel === x;
    }
    return y !== null && gate.xChannel === x && gate.yChannel === y;
  });
}

/**
 * Moves every point of a gate: the plots move gates in axis positions, then convert back, so
 * that a gate keeps its shape on a logarithmic axis.
 *
 * @param fx new horizontal value of a point, from the old one
 * @param fy new vertical value of a point, from the old one
 */
export function mapPoints(gate: Gate, fx: (v: number) => number, fy: (v: number) => number): Gate {
  switch (gate.type) {
    case 'rectangle':
      return {
        ...gate,
        xMin: fx(gate.xMin),
        xMax: fx(gate.xMax),
        yMin: fy(gate.yMin),
        yMax: fy(gate.yMax),
      };
    case 'polygon':
      return { ...gate, xs: gate.xs.map(fx), ys: gate.ys.map(fy) };
    case 'quadrant':
      return { ...gate, xThreshold: fx(gate.xThreshold), yThreshold: fy(gate.yThreshold) };
    case 'interval':
      return { ...gate, min: fx(gate.min), max: fx(gate.max) };
  }
}
