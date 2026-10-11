import { describe, expect, it } from 'vitest';
import { decades, jitter, niceTicks, predict } from './chartMath';

describe('chartMath', () => {
  it('chooses round ticks', () => {
    expect(niceTicks(0, 100)).toEqual([0, 20, 40, 60, 80, 100]);
    expect(niceTicks(-0.3, 0.3)).toEqual([-0.4, -0.2, 0, 0.2, 0.4]);
    expect(niceTicks(5, 5)).toEqual([5]);
    expect(niceTicks(0, 78.5)).toEqual([0, 20, 40, 60, 80]);
    expect(niceTicks(-3, 41)).toEqual([-10, 0, 10, 20, 30, 40, 50]);
  });

  it('predicts the response of a four-parameter logistic, as the server', () => {
    const fit = {
      bottom: 0,
      top: 100,
      ec50: 10,
      hill: 1,
      ec50Low: null,
      ec50High: null,
      rSquared: 1,
      pointCount: 5,
    };
    expect(predict(fit, 10)).toBeCloseTo(50);
    expect(predict(fit, 0)).toBe(0);
    expect(predict({ ...fit, hill: -1 }, 0)).toBe(100);
    expect(predict(fit, 1000)).toBeCloseTo(99, 0);
  });

  it('lists the decades and spreads the replicates', () => {
    expect(decades(-0.5, 1.2)).toEqual([-1, 0, 1, 2]);
    expect(jitter(0, 1)).toBe(0);
    expect([0, 1, 2].map((i) => jitter(i, 3))).toEqual([-1, 0, 1]);
  });
});
