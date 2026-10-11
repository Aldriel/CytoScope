import { useEffect, useMemo, useRef, useState, type PointerEvent } from 'react';
import { useMessages } from '../../i18n';
import { mapPoints } from '../../model/gating';
import type { Axis, Gate, Plot, QuadrantGate } from '../../model/types';
import {
  clampX,
  clampY,
  densityPixels,
  distance,
  histogramPath,
  MARGIN,
  PLOT_SIZE,
  px,
  py,
  valueX,
  valueY,
  VIEW_HEIGHT,
  VIEW_WIDTH,
  visibleLabels,
  xValue,
  yValue,
  type Point,
} from './geometry';

/** Drawing tools of the plots. */
export const TOOLS = ['select', 'rectangle', 'polygon', 'quadrant', 'interval'] as const;
export type Tool = (typeof TOOLS)[number];

/** A gate drawn on the plot, with the population it defines. */
export interface DrawnGate {
  id: string;
  name: string;
  gate: Gate;
  /** Share shown next to the name; by default the share of the parent counted for the plot. */
  percent?: number | null;
  /** Shown only: the gate cannot be dragged. */
  locked?: boolean;
}

interface Props {
  plot: Plot;
  /** Gates drawn on the axes of the plot. */
  gates: readonly DrawnGate[];
  tool: Tool;
  /** Accessible name of the plot. */
  label: string;
  /** A gate has been drawn; a quadrant gate gives the cross of the four regions. */
  onCreate?: (gate: Gate) => void;
  /** A gate has been dragged to a new place. */
  onMove?: (id: string, gate: Gate) => void;
  /** A gate has been clicked. */
  onOpen?: (id: string) => void;
}

/** Distance, in SVG units, under which a click closes a polygon on its first vertex. */
const CLOSE_DISTANCE = 8;
/** Smallest drag, in SVG units, that draws or moves a gate rather than clicking it. */
const MIN_DRAG = 3;

type Draft =
  | { kind: 'box'; start: Point; current: Point }
  | { kind: 'polygon'; points: Point[]; cursor: Point | null };

interface Drag {
  id: string;
  gate: Gate;
  start: Point;
  current: Point;
}

/** Draws the image of a density in a data address, or null where canvases are not available. */
function densityImage(plot: Plot): string | null {
  const density = plot.density;
  if (density === null) {
    return null;
  }
  const canvas = document.createElement('canvas');
  canvas.width = density.bins;
  canvas.height = density.bins;
  const context = canvas.getContext('2d');
  if (context === null) {
    return null;
  }
  const image = context.createImageData(density.bins, density.bins);
  image.data.set(densityPixels(density));
  context.putImageData(image, 0, 0);
  return canvas.toDataURL();
}

/** Same channels and thresholds: the regions of one quadrant cross. */
const sameCross = (a: QuadrantGate, b: QuadrantGate) =>
  a.xChannel === b.xChannel &&
  a.yChannel === b.yChannel &&
  a.xThreshold === b.xThreshold &&
  a.yThreshold === b.yThreshold;

/**
 * Cytometry plot: a density plot of two channels, or a histogram of one, with the gates of the
 * child populations. Depending on the tool, dragging draws a new gate or moves an existing one.
 */
export function FlowPlot({ plot, gates, tool, label, onCreate, onMove, onOpen }: Props) {
  const m = useMessages();
  const svg = useRef<SVGSVGElement>(null);
  const [draft, setDraft] = useState<Draft | null>(null);
  const [drag, setDrag] = useState<Drag | null>(null);
  const image = useMemo(() => densityImage(plot), [plot]);
  const xAxis = plot.x;
  const yAxis = plot.y;

  useEffect(() => setDraft(null), [tool, plot.x.channel, plot.y?.channel]);
  useEffect(() => {
    if (draft === null) {
      return;
    }
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setDraft(null);
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [draft]);

  const stats = new Map(plot.populations.map((p) => [p.populationId, p]));

  /** Coordinates of a pointer event in SVG units. */
  const toSvg = (e: { clientX: number; clientY: number }): Point => {
    const rect = svg.current!.getBoundingClientRect();
    const sx = rect.width > 0 ? VIEW_WIDTH / rect.width : 1;
    const sy = rect.height > 0 ? VIEW_HEIGHT / rect.height : 1;
    return { x: (e.clientX - rect.left) * sx, y: (e.clientY - rect.top) * sy };
  };
  const clamp = (p: Point): Point => ({ x: clampX(p.x), y: clampY(p.y) });

  /** Gate moved by a drag, in axis positions so that it keeps its shape on any scale. */
  const moved = (gate: Gate, d: Drag): Gate => {
    const dx = d.current.x - d.start.x;
    const dy = d.current.y - d.start.y;
    return mapPoints(
      gate,
      (v) => xValue(xAxis, valueX(xAxis, v) + dx),
      (v) => (yAxis === null ? v : yValue(yAxis, valueY(yAxis, v) + dy)),
    );
  };

  /** Gate as shown, moved when it, or its quadrant cross, is being dragged. */
  const shown = (g: DrawnGate): Gate => {
    if (drag === null) {
      return g.gate;
    }
    if (drag.id === g.id) {
      return moved(g.gate, drag);
    }
    if (
      g.gate.type === 'quadrant' &&
      drag.gate.type === 'quadrant' &&
      sameCross(g.gate, drag.gate)
    ) {
      return moved(g.gate, drag);
    }
    return g.gate;
  };

  const finishPolygon = (points: Point[]) => {
    setDraft(null);
    if (yAxis !== null) {
      onCreate?.({
        type: 'polygon',
        xChannel: xAxis.channel,
        yChannel: yAxis.channel,
        xs: points.map((p) => xValue(xAxis, p.x)),
        ys: points.map((p) => yValue(yAxis, p.y)),
      });
    }
  };

  const onPointerDown = (e: PointerEvent<SVGSVGElement>) => {
    const point = clamp(toSvg(e));
    if (tool === 'select') {
      const target = (e.target as Element).closest?.('[data-gate-id]');
      const id = target?.getAttribute('data-gate-id');
      const gate = gates.find((g) => g.id === id);
      if (gate && !gate.locked) {
        e.currentTarget.setPointerCapture?.(e.pointerId);
        setDrag({ id: gate.id, gate: gate.gate, start: point, current: point });
      }
    } else if (tool === 'polygon') {
      const points = draft?.kind === 'polygon' ? draft.points : [];
      const last = points[points.length - 1];
      if (points.length >= 3 && distance(point, points[0]) <= CLOSE_DISTANCE) {
        finishPolygon(points);
      } else if (last === undefined || distance(point, last) > MIN_DRAG) {
        setDraft({ kind: 'polygon', points: [...points, point], cursor: point });
      }
    } else if (tool === 'quadrant') {
      if (yAxis !== null) {
        onCreate?.({
          type: 'quadrant',
          xChannel: xAxis.channel,
          yChannel: yAxis.channel,
          xThreshold: xValue(xAxis, point.x),
          yThreshold: yValue(yAxis, point.y),
          quadrant: 'NEGATIVE_NEGATIVE',
        });
      }
    } else {
      e.currentTarget.setPointerCapture?.(e.pointerId);
      setDraft({ kind: 'box', start: point, current: point });
    }
  };

  const onPointerMove = (e: PointerEvent<SVGSVGElement>) => {
    if (drag === null && draft === null) {
      return;
    }
    const point = clamp(toSvg(e));
    if (drag !== null) {
      setDrag({ ...drag, current: point });
    } else if (draft?.kind === 'box') {
      setDraft({ ...draft, current: point });
    } else if (draft?.kind === 'polygon') {
      setDraft({ ...draft, cursor: point });
    }
  };

  const onPointerUp = () => {
    if (drag !== null) {
      setDrag(null);
      if (distance(drag.start, drag.current) < MIN_DRAG) {
        onOpen?.(drag.id);
      } else {
        onMove?.(drag.id, moved(drag.gate, drag));
      }
      return;
    }
    if (draft?.kind !== 'box') {
      return;
    }
    setDraft(null);
    const { start, current } = draft;
    if (Math.abs(start.x - current.x) < MIN_DRAG) {
      return;
    }
    const [x1, x2] = [Math.min(start.x, current.x), Math.max(start.x, current.x)];
    if (tool === 'interval') {
      onCreate?.({
        type: 'interval',
        xChannel: xAxis.channel,
        min: xValue(xAxis, x1),
        max: xValue(xAxis, x2),
      });
    } else if (yAxis !== null && Math.abs(start.y - current.y) >= MIN_DRAG) {
      const [top, bottom] = [Math.min(start.y, current.y), Math.max(start.y, current.y)];
      onCreate?.({
        type: 'rectangle',
        xChannel: xAxis.channel,
        yChannel: yAxis.channel,
        xMin: xValue(xAxis, x1),
        xMax: xValue(xAxis, x2),
        yMin: yValue(yAxis, bottom),
        yMax: yValue(yAxis, top),
      });
    }
  };

  const onDoubleClick = () => {
    if (draft?.kind === 'polygon' && draft.points.length >= 3) {
      finishPolygon(draft.points);
    }
  };

  const percentOf = (id: string) => m.percent(stats.get(id)?.percentOfParent ?? null);

  return (
    <svg
      ref={svg}
      className={`flow-plot tool-${tool}`}
      viewBox={`0 0 ${VIEW_WIDTH} ${VIEW_HEIGHT}`}
      role="application"
      aria-label={label}
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={onPointerUp}
      onDoubleClick={onDoubleClick}
    >
      <rect className="plot-area" x={px(0)} y={py(1)} width={PLOT_SIZE} height={PLOT_SIZE} />
      {image !== null && (
        <image
          href={image}
          x={px(0)}
          y={py(1)}
          width={PLOT_SIZE}
          height={PLOT_SIZE}
          preserveAspectRatio="none"
          className="density"
        />
      )}
      {plot.histogram !== null && <path className="histogram" d={histogramPath(plot.histogram)} />}
      <AxisTicks axis={xAxis} horizontal />
      {yAxis !== null && <AxisTicks axis={yAxis} horizontal={false} />}
      <text className="axis-title" x={px(0.5)} y={VIEW_HEIGHT - 6} textAnchor="middle">
        {xAxis.displayName}
      </text>
      {yAxis !== null && (
        <text
          className="axis-title"
          transform={`translate(14 ${py(0.5)}) rotate(-90)`}
          textAnchor="middle"
        >
          {yAxis.displayName}
        </text>
      )}
      {gates.map((g, index) => (
        <GateShape
          key={g.id}
          id={g.id}
          name={g.name}
          gate={shown(g)}
          index={index}
          x={xAxis}
          y={yAxis}
          percent={g.percent === undefined ? percentOf(g.id) : m.percent(g.percent)}
        />
      ))}
      {draft?.kind === 'box' &&
        (tool === 'interval' ? (
          <rect
            className="draft"
            x={Math.min(draft.start.x, draft.current.x)}
            y={py(1)}
            width={Math.abs(draft.current.x - draft.start.x)}
            height={PLOT_SIZE}
          />
        ) : (
          <rect
            className="draft"
            x={Math.min(draft.start.x, draft.current.x)}
            y={Math.min(draft.start.y, draft.current.y)}
            width={Math.abs(draft.current.x - draft.start.x)}
            height={Math.abs(draft.current.y - draft.start.y)}
          />
        ))}
      {draft?.kind === 'polygon' && (
        <polyline
          className="draft"
          points={[...draft.points, ...(draft.cursor ? [draft.cursor] : [])]
            .map((p) => `${p.x},${p.y}`)
            .join(' ')}
        />
      )}
    </svg>
  );
}

/** Ticks and labels of an axis. */
function AxisTicks({ axis, horizontal }: { axis: Axis; horizontal: boolean }) {
  const labelled = visibleLabels(axis, horizontal);
  return (
    <g className="axis">
      {axis.ticks.map((tick) =>
        horizontal ? (
          <g key={tick.position}>
            <line x1={px(tick.position)} x2={px(tick.position)} y1={py(0)} y2={py(0) + 5} />
            {labelled.has(tick.position) && (
              <text x={px(tick.position)} y={py(0) + 17} textAnchor="middle">
                {tick.label}
              </text>
            )}
          </g>
        ) : (
          <g key={tick.position}>
            <line x1={px(0) - 5} x2={px(0)} y1={py(tick.position)} y2={py(tick.position)} />
            {labelled.has(tick.position) && (
              <text x={px(0) - 8} y={py(tick.position) + 4} textAnchor="end">
                {tick.label}
              </text>
            )}
          </g>
        ),
      )}
    </g>
  );
}

interface GateProps {
  id: string;
  name: string;
  gate: Gate;
  /** Rank among the gates, to stack the labels of the intervals. */
  index: number;
  x: Axis;
  y: Axis | null;
  percent: string;
}

/** Outline of a gate, with its name and the share of the parent it holds. */
function GateShape({ id, name, gate, index, x, y, percent }: GateProps) {
  const text = `${name} ${percent}`;
  switch (gate.type) {
    case 'rectangle': {
      if (y === null) {
        return null;
      }
      const left = valueX(x, gate.xMin);
      const top = valueY(y, gate.yMax);
      return (
        <g className="gate" data-gate-id={id}>
          <title>{text}</title>
          <rect
            x={left}
            y={top}
            width={valueX(x, gate.xMax) - left}
            height={valueY(y, gate.yMin) - top}
          />
          <text x={left + 3} y={top - 4}>
            {text}
          </text>
        </g>
      );
    }
    case 'polygon': {
      if (y === null) {
        return null;
      }
      const points = gate.xs.map((v, i) => `${valueX(x, v)},${valueY(y, gate.ys[i])}`);
      const top = Math.min(...gate.ys.map((v) => valueY(y, v)));
      const left = Math.min(...gate.xs.map((v) => valueX(x, v)));
      return (
        <g className="gate" data-gate-id={id}>
          <title>{text}</title>
          <polygon points={points.join(' ')} />
          <text x={left + 3} y={top - 4}>
            {text}
          </text>
        </g>
      );
    }
    case 'quadrant': {
      if (y === null) {
        return null;
      }
      const cx = clampX(valueX(x, gate.xThreshold));
      const cy = clampY(valueY(y, gate.yThreshold));
      const right = gate.quadrant === 'POSITIVE_NEGATIVE' || gate.quadrant === 'POSITIVE_POSITIVE';
      const upper = gate.quadrant === 'NEGATIVE_POSITIVE' || gate.quadrant === 'POSITIVE_POSITIVE';
      const left = right ? cx : px(0);
      const top = upper ? py(1) : cy;
      return (
        <g className="gate quadrant" data-gate-id={id}>
          <title>{text}</title>
          <rect
            className="region"
            x={left}
            y={top}
            width={right ? px(1) - cx : cx - px(0)}
            height={upper ? cy - py(1) : py(0) - cy}
          />
          <line x1={cx} x2={cx} y1={py(1)} y2={py(0)} />
          <line x1={px(0)} x2={px(1)} y1={cy} y2={cy} />
          <text
            x={right ? px(1) - 4 : px(0) + 4}
            y={upper ? py(1) + 14 : py(0) - 6}
            textAnchor={right ? 'end' : 'start'}
          >
            {text}
          </text>
        </g>
      );
    }
    case 'interval': {
      const left = valueX(x, gate.min);
      const right = valueX(x, gate.max);
      const bar = MARGIN.top + 14 + (index % 6) * 16;
      return (
        <g className="gate interval" data-gate-id={id}>
          <title>{text}</title>
          <rect className="region" x={left} y={py(1)} width={right - left} height={PLOT_SIZE} />
          <line x1={left} x2={right} y1={bar} y2={bar} />
          <line x1={left} x2={left} y1={bar - 5} y2={bar + 5} />
          <line x1={right} x2={right} y1={bar - 5} y2={bar + 5} />
          <text x={(left + right) / 2} y={bar - 4} textAnchor="middle">
            {text}
          </text>
        </g>
      );
    }
  }
}
