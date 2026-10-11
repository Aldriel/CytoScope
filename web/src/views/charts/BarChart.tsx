import { useMessages } from '../../i18n';
import type { GroupResult } from '../../model/types';
import { jitter, niceTicks } from './chartMath';

const WIDTH = 560;
const HEIGHT = 280;
const MARGIN = { left: 52, right: 12, top: 24, bottom: 48 };
const PLOT_W = WIDTH - MARGIN.left - MARGIN.right;
const PLOT_H = HEIGHT - MARGIN.top - MARGIN.bottom;

interface Props {
  groups: readonly GroupResult[];
  controlGroup: string;
  /** Name of the value compared, the title of the vertical axis. */
  label: string;
}

/**
 * Mean and standard deviation of each group as bars, the replicates as points, and the stars of
 * Dunnett's test above the groups that differ from the control.
 */
export function BarChart({ groups, controlGroup, label }: Props) {
  const m = useMessages();
  const values = groups.flatMap((g) => [g.mean + (g.standardDeviation ?? 0), ...g.values]);
  const top = Math.max(1, ...values) * 1.15;
  const ticks = niceTicks(Math.min(0, ...groups.flatMap((g) => g.values)), top);
  const bottom = ticks[0];
  const high = ticks[ticks.length - 1];
  const y = (v: number) => MARGIN.top + PLOT_H * (1 - (v - bottom) / (high - bottom));
  const slot = PLOT_W / groups.length;
  const bar = Math.min(48, slot * 0.6);

  return (
    <svg
      className="chart bar-chart"
      viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
      role="img"
      aria-label={label}
    >
      {ticks.map((t) => (
        <g key={t} className="axis">
          <line x1={MARGIN.left} x2={WIDTH - MARGIN.right} y1={y(t)} y2={y(t)} className="grid" />
          <text x={MARGIN.left - 6} y={y(t) + 4} textAnchor="end">
            {m.number(t, t % 1 === 0 ? 0 : 1)}
          </text>
        </g>
      ))}
      <text
        className="axis-title"
        transform={`translate(13 ${MARGIN.top + PLOT_H / 2}) rotate(-90)`}
        textAnchor="middle"
      >
        {label}
      </text>
      {groups.map((g, i) => {
        const cx = MARGIN.left + slot * (i + 0.5);
        const sd = g.standardDeviation ?? 0;
        return (
          <g key={g.name} className={g.name === controlGroup ? 'group control' : 'group'}>
            <title>{`${g.name}: ${m.number(g.mean, 1)} ± ${m.number(g.standardDeviation, 1)}`}</title>
            <rect
              x={cx - bar / 2}
              y={y(Math.max(0, g.mean))}
              width={bar}
              height={Math.abs(y(0) - y(g.mean))}
            />
            {sd > 0 && (
              <g className="error-bar">
                <line x1={cx} x2={cx} y1={y(g.mean - sd)} y2={y(g.mean + sd)} />
                <line x1={cx - 6} x2={cx + 6} y1={y(g.mean + sd)} y2={y(g.mean + sd)} />
                <line x1={cx - 6} x2={cx + 6} y1={y(g.mean - sd)} y2={y(g.mean - sd)} />
              </g>
            )}
            {g.values.map((v, k) => (
              <circle
                key={k}
                className="replicate"
                cx={cx + (jitter(k, g.values.length) * bar) / 3}
                cy={y(v)}
                r={3}
              />
            ))}
            {g.stars !== '' && (
              <text className="stars" x={cx} y={y(g.mean + sd) - 6} textAnchor="middle">
                {g.stars}
              </text>
            )}
            <text className="group-name" x={cx} y={HEIGHT - MARGIN.bottom + 16} textAnchor="middle">
              {g.name}
            </text>
          </g>
        );
      })}
      <line className="baseline" x1={MARGIN.left} x2={WIDTH - MARGIN.right} y1={y(0)} y2={y(0)} />
    </svg>
  );
}
