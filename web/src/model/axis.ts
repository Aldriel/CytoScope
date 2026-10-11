/**
 * Conversion between data values and positions on a plot axis (0 at the left or bottom, 1 at the
 * right or top). The server sends, for each axis, the value at 257 evenly spaced positions; the
 * browser interpolates in this table, so it needs no copy of the logicle or arcsinh formulas.
 */
import type { Axis } from './types';

/** Number of intervals of the conversion table. */
const STEPS = 256;

/**
 * Converts a position to a data value.
 *
 * @param axis the axis
 * @param position the position; outside 0–1, the first or last interval is extended
 */
export function toValue(axis: Pick<Axis, 'values'>, position: number): number {
  const values = axis.values;
  const steps = values.length - 1;
  const scaled = position * steps;
  const i = Math.min(steps - 1, Math.max(0, Math.floor(scaled)));
  const t = scaled - i;
  return values[i] + t * (values[i + 1] - values[i]);
}

/**
 * Converts a data value to a position.
 *
 * @param axis the axis
 * @param value the value; outside the table, the first or last interval is extended
 */
export function toPosition(axis: Pick<Axis, 'values'>, value: number): number {
  const values = axis.values;
  const steps = values.length - 1;
  let low = 0;
  let high = steps;
  while (high - low > 1) {
    const middle = (low + high) >> 1;
    if (values[middle] <= value) {
      low = middle;
    } else {
      high = middle;
    }
  }
  const width = values[high] - values[low];
  const t = width === 0 ? 0 : (value - values[low]) / width;
  return (low + t) / steps;
}

/** Number of intervals of the tables sent by the server. */
export const AXIS_STEPS = STEPS;
