import { useEffect, useState } from 'react';
import { useMessages } from '../i18n';
import type { AppModel } from '../model/AppModel';
import { channelsOf, withoutDanglingReferences } from '../model/experiment';
import {
  add,
  addQuadrants,
  drawableChildren,
  find,
  flatten,
  gateChannels,
  moveGate,
  parentOf,
  remove,
  rename,
  ROOT_ID,
} from '../model/gating';
import {
  QUADRANTS,
  type ChannelView,
  type Gate,
  type Population,
  type ScaleType,
  type Workspace,
} from '../model/types';
import type { DrawnGate, Tool } from '../views/plot/FlowPlot';
import { usePlot } from './usePlot';
import { useStore } from './useStore';

/** Axes of a plot: two channels, or one for a histogram. */
interface Axes {
  x: string;
  y: string | null;
}

/** Usual names of the scatter channels, shown first. */
const FORWARD = /^FSC-A$|^FSC/;
const SIDE = /^SSC-A$|^SSC/;

/**
 * Axes on which to show a population: those of the gates of its children, if any, so that they
 * can be seen and adjusted; otherwise forward versus side scatter.
 */
export function axesFor(population: Population, channels: readonly ChannelView[]): Axes {
  const child = population.children.find((c) => c.gate !== null);
  if (child?.gate) {
    const [x, y] = gateChannels(child.gate);
    return { x, y };
  }
  const names = channels.map((c) => c.name);
  const x = names.find((n) => FORWARD.test(n)) ?? names[0] ?? '';
  const y = names.find((n) => SIDE.test(n)) ?? names.find((n) => n !== x) ?? null;
  return { x, y };
}

/** Tube shown first: the first sample, or the first tube. */
const firstTube = (workspace: Workspace | null) => {
  const samples = workspace?.experiment.samples ?? [];
  return (samples.find((s) => s.role === 'SAMPLE') ?? samples[0])?.id ?? null;
};

/**
 * Controller of the "Gating" tab: the tube, population and axes shown, the drawing tool, and the
 * changes of the hierarchy. The gates are shared by every tube.
 *
 * @param active whether the tab is shown; the plot is counted only then
 */
export function useGatingController(model: AppModel, active: boolean) {
  const m = useMessages();
  const workspace = useStore(model.workspace);
  const [sampleId, setSampleId] = useState<string | null>(null);
  const [populationId, setPopulationId] = useState(ROOT_ID);
  const [axes, setAxes] = useState<Axes>({ x: '', y: null });
  const [scales, setScales] = useState<Record<string, ScaleType>>({});
  const [tool, setTool] = useState<Tool>('select');

  const channels = channelsOf(workspace);
  const names = channels.map((c) => c.name);
  const tree = workspace?.experiment.gating ?? null;
  const population = (tree && find(tree, populationId)) ?? tree;

  // Keeps the choices valid when the tubes or the hierarchy change.
  const sampleValid = workspace?.samples.some((s) => s.id === sampleId) ?? false;
  const axesValid = names.includes(axes.x) && (axes.y === null || names.includes(axes.y));
  useEffect(() => {
    if (workspace === null || tree === null) {
      return;
    }
    if (!sampleValid) {
      setSampleId(firstTube(workspace));
    }
    if (population !== null && population.id !== populationId) {
      setPopulationId(population.id);
    }
    if (!axesValid && population !== null) {
      setAxes(axesFor(population, channelsOf(workspace)));
    }
  }, [workspace, tree, sampleValid, population, populationId, axesValid]);

  const request =
    active &&
    workspace !== null &&
    sampleValid &&
    axesValid &&
    sampleId !== null &&
    population !== null
      ? {
          sampleId,
          populationId: population.id,
          x: axes.x,
          y: axes.y,
          xScale: scales[axes.x] ?? null,
          yScale: axes.y === null ? null : (scales[axes.y] ?? null),
        }
      : null;
  const plot = usePlot(model, request);

  const gates: DrawnGate[] =
    population === null
      ? []
      : drawableChildren(population, axes.x, axes.y).map((c) => ({
          id: c.id,
          name: c.name,
          gate: c.gate as Gate,
        }));

  const changeTree = (change: (root: Population) => Population) =>
    model.updateExperiment((e) => withoutDanglingReferences({ ...e, gating: change(e.gating) }));

  const displayName = (channel: string) =>
    channels.find((c) => c.name === channel)?.displayName ?? channel;

  /** Shows a population, on the axes of its gates. */
  const open = (id: string) => {
    const target = tree && find(tree, id);
    if (target) {
      setPopulationId(id);
      if (target.children.some((c) => c.gate !== null)) {
        setAxes(axesFor(target, channels));
      }
    }
  };

  return {
    workspace,
    channels,
    sampleId,
    population,
    axes,
    tool,
    plot,
    gates,
    populations: tree ? flatten(tree) : [],
    /** Statistics of every population in the tube shown. */
    stats: new Map(plot?.populations.map((p) => [p.populationId, p]) ?? []),
    selectSample: setSampleId,
    open,
    setTool,
    setAxis: (axis: 'x' | 'y', channel: string | null) =>
      setAxes((current) =>
        axis === 'x' ? { ...current, x: channel ?? current.x } : { ...current, y: channel },
      ),
    setScale: (channel: string, scale: ScaleType) =>
      setScales((current) => ({ ...current, [channel]: scale })),
    /** Adds the populations of a new gate under the population shown. */
    create: (gate: Gate) => {
      const parentId = population?.id ?? ROOT_ID;
      if (gate.type === 'quadrant') {
        const x = displayName(gate.xChannel);
        const y = displayName(gate.yChannel);
        const regionNames = QUADRANTS.map((q) => m.gating.quadrantName(x, y, q));
        void changeTree((root) =>
          addQuadrants(
            root,
            parentId,
            regionNames,
            gate.xChannel,
            gate.yChannel,
            gate.xThreshold,
            gate.yThreshold,
          ),
        );
      } else {
        void changeTree((root) =>
          add(root, parentId, m.gating.defaultName(flatten(root).length), gate),
        );
      }
      setTool('select');
    },
    move: (id: string, gate: Gate) => void changeTree((root) => moveGate(root, id, gate)),
    rename: (id: string, name: string) => {
      if (name.trim() !== '') {
        void changeTree((root) => rename(root, id, name.trim()));
      }
    },
    remove: (id: string) => {
      if (
        tree &&
        population &&
        flatten(find(tree, id) ?? tree).some((p) => p.population.id === population.id)
      ) {
        setPopulationId(parentOf(tree, id)?.id ?? ROOT_ID);
      }
      void changeTree((root) => remove(root, id));
    },
    exportUrl: (populationId: string) =>
      sampleId === null ? null : model.exportUrl(sampleId, populationId),
  };
}

export type GatingController = ReturnType<typeof useGatingController>;
