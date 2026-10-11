/**
 * Layout and colours of the plots, as pure functions so that they can be tested without a
 * browser. Positions on an axis go from 0 to 1; the plot area of the SVG is PLOT_SIZE units wide.
 */
import { toPosition, toValue } from '../../model/axis';
import type { Axis, Density } from '../../model/types';

/** Side of the plot area, in SVG units. */
export const PLOT_SIZE = 320;
/** Margins around the plot area, for the ticks and the axis titles. */
export const MARGIN = { left: 58, right: 12, top: 12, bottom: 44 };
export const VIEW_WIDTH = MARGIN.left + PLOT_SIZE + MARGIN.right;
export const VIEW_HEIGHT = MARGIN.top + PLOT_SIZE + MARGIN.bottom;

/** A point in SVG units. */
export interface Point {
  x: number;
  y: number;
}

/** SVG abscissa of a position on the horizontal axis. */
export const px = (position: number) => MARGIN.left + position * PLOT_SIZE;
/** SVG ordinate of a position on the vertical axis, which goes upward. */
export const py = (position: number) => MARGIN.top + (1 - position) * PLOT_SIZE;
/** Position on the horizontal axis of an SVG abscissa. */
export const positionX = (x: number) => (x - MARGIN.left) / PLOT_SIZE;
/** Position on the vertical axis of an SVG ordinate. */
export const positionY = (y: number) => 1 - (y - MARGIN.top) / PLOT_SIZE;

/** SVG abscissa of a data value. */
export const valueX = (axis: Axis, value: number) => px(toPosition(axis, value));
/** SVG ordinate of a data value. */
export const valueY = (axis: Axis, value: number) => py(toPosition(axis, value));

/** Smallest distances, in SVG units, between two tick labels of a horizontal or vertical axis. */
const MIN_LABEL_GAP = { horizontal: 30, vertical: 14 };

/**
 * Positions of the ticks whose label is drawn. Near zero on a logicle or arcsinh axis, the
 * decades of both signs are drawn close together: zero, then the largest values, keep their
 * label, and a label that would overlap one already kept is skipped.
 */
export function visibleLabels(axis: Axis, horizontal: boolean): Set<number> {
  const gap = (horizontal ? MIN_LABEL_GAP.horizontal : MIN_LABEL_GAP.vertical) / PLOT_SIZE;
  const byPriority = axis.ticks
    .filter((tick) => tick.label !== '')
    .sort((a, b) =>
      a.value === 0 ? -1 : b.value === 0 ? 1 : Math.abs(b.value) - Math.abs(a.value),
    );
  const shown = new Set<number>();
  for (const tick of byPriority) {
    if ([...shown].every((position) => Math.abs(position - tick.position) >= gap)) {
      shown.add(tick.position);
    }
  }
  return shown;
}

/** Data value under an SVG abscissa. */
export const xValue = (axis: Axis, x: number) => toValue(axis, positionX(x));
/** Data value under an SVG ordinate. */
export const yValue = (axis: Axis, y: number) => toValue(axis, positionY(y));

/** Keeps a coordinate inside the plot area. */
export const clampX = (x: number) => Math.min(px(1), Math.max(px(0), x));
export const clampY = (y: number) => Math.min(py(0), Math.max(py(1), y));

/**
 * Pseudo-colour of a density, as in most cytometry software: blue for the sparse events, then
 * cyan, green, yellow and red for the densest.
 *
 * @param t relative density, from 0 to 1
 * @returns red, green and blue, from 0 to 255
 */
export function heat(t: number): [number, number, number] {
  const stops: [number, number, number][] = [
    [30, 60, 200],
    [0, 190, 230],
    [40, 200, 70],
    [250, 220, 0],
    [230, 30, 20],
  ];
  const scaled = Math.min(1, Math.max(0, t)) * (stops.length - 1);
  const i = Math.min(stops.length - 2, Math.floor(scaled));
  const f = scaled - i;
  return stops[i].map((c, k) => Math.round(c + f * (stops[i + 1][k] - c))) as [
    number,
    number,
    number,
  ];
}

/**
 * Pixels of a density plot, one per cell of the grid, the top row first. Densities are on a
 * logarithmic scale so that rare events stay visible; empty cells are transparent.
 *
 * @returns RGBA values, four per pixel
 */
export function densityPixels(density: Density): Uint8ClampedArray {
  const { bins, counts, max } = density;
  const pixels = new Uint8ClampedArray(bins * bins * 4);
  const top = Math.log1p(max);
  for (let x = 0; x < bins; x++) {
    for (let y = 0; y < bins; y++) {
      const count = counts[x * bins + y];
      if (count > 0) {
        const [r, g, b] = heat(top === 0 ? 1 : Math.log1p(count) / top);
        const offset = ((bins - 1 - y) * bins + x) * 4;
        pixels[offset] = r;
        pixels[offset + 1] = g;
        pixels[offset + 2] = b;
        pixels[offset + 3] = 255;
      }
    }
  }
  return pixels;
}

/** SVG path of a histogram, as steps filling the plot area. */
export function histogramPath(counts: readonly number[]): string {
  const max = Math.max(1, ...counts);
  const width = PLOT_SIZE / counts.length;
  const base = py(0);
  let path = `M${px(0)},${base}`;
  counts.forEach((count, i) => {
    const top = MARGIN.top + PLOT_SIZE * (1 - (0.95 * count) / max);
    path += `V${top.toFixed(1)}H${(px(0) + (i + 1) * width).toFixed(1)}`;
  });
  return `${path}V${base}Z`;
}

/** Distance between two points. */
export const distance = (a: Point, b: Point) => Math.hypot(a.x - b.x, a.y - b.y);
