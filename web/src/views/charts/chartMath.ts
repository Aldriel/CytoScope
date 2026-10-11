/** Calculations of the result charts, kept apart so that they can be tested alone. */
import type { Fit } from '../../model/types';

/**
 * Round tick values covering a range: steps of 1, 2 or 5 times a power of ten, from the last
 * tick at or below min to the first tick at or above max, so the axis holds every value.
 *
 * @param target approximate number of intervals
 */
export function niceTicks(min: number, max: number, target = 5): number[] {
  if (!(max > min)) {
    return [min];
  }
  const raw = (max - min) / target;
  const power = 10 ** Math.floor(Math.log10(raw));
  const step = [1, 2, 5, 10].map((f) => f * power).find((s) => s >= raw) as number;
  const first = Math.floor(min / step + 1e-9);
  const last = Math.ceil(max / step - 1e-9);
  const ticks: number[] = [];
  for (let i = first; i <= last; i++) {
    ticks.push(i === 0 ? 0 : Number((i * step).toPrecision(12)));
  }
  return ticks;
}

/** Response predicted by a four-parameter logistic fit, as FourParameterLogistic.predict. */
export function predict(fit: Fit, dose: number): number {
  if (dose <= 0) {
    return fit.hill > 0 ? fit.bottom : fit.top;
  }
  const ratio = 10 ** ((Math.log10(fit.ec50) - Math.log10(dose)) * fit.hill);
  return fit.bottom + (fit.top - fit.bottom) / (1 + ratio);
}

/** Powers of ten from 10^low to 10^high, the ticks of a logarithmic axis. */
export function decades(low: number, high: number): number[] {
  const ticks: number[] = [];
  for (let e = Math.floor(low); e <= Math.ceil(high); e++) {
    ticks.push(e);
  }
  return ticks;
}

/** Offset, from -1 to 1, spreading the replicates of a bar so that they do not overlap. */
export const jitter = (index: number, count: number) =>
  count <= 1 ? 0 : (index / (count - 1)) * 2 - 1;
