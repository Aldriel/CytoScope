import { useMessages } from '../../i18n';
import type { Curve } from '../../model/types';
import { decades, niceTicks, predict } from './chartMath';

const WIDTH = 560;
const HEIGHT = 300;
const MARGIN = { left: 52, right: 16, top: 16, bottom: 44 };
const PLOT_W = WIDTH - MARGIN.left - MARGIN.right;
const PLOT_H = HEIGHT - MARGIN.top - MARGIN.bottom;
/** Points of the drawn curve. */
const STEPS = 120;

interface Props {
  curve: Curve;
  /** Name of the value measured, the title of the vertical axis. */
  label: string;
}

/**
 * Dose-response curve: the replicates as points on a logarithmic dose axis, the fitted
 * four-parameter logistic and a dotted line at the EC50. Untreated tubes (dose 0) are drawn one
 * decade below the lowest dose, after a break of the axis.
 */
export function DoseCurve({ curve, label }: Props) {
  const m = useMessages();
  const { points, fit } = curve;
  const doses = points.map((p) => p.dose).filter((d) => d > 0);
  const low = Math.log10(Math.min(...doses, fit.ec50)) - 0.3;
  const high = Math.log10(Math.max(...doses, fit.ec50)) + 0.3;
  const zero = low - 0.7;
  const hasZero = points.some((p) => p.dose <= 0);
  const left = hasZero ? zero - 0.3 : low;
  const x = (logDose: number) => MARGIN.left + (PLOT_W * (logDose - left)) / (high - left);
  const xDose = (dose: number) => x(dose > 0 ? Math.log10(dose) : zero);

  const values = points.map((p) => p.value);
  const ticks = niceTicks(Math.min(0, ...values, fit.bottom), Math.max(...values, fit.top) * 1.08);
  const bottom = ticks[0];
  const top = ticks[ticks.length - 1];
  const y = (v: number) => MARGIN.top + PLOT_H * (1 - (v - bottom) / (top - bottom));

  const path = Array.from({ length: STEPS + 1 }, (_, i) => {
    const logDose = low + ((high - low) * i) / STEPS;
    return `${i === 0 ? 'M' : 'L'}${x(logDose).toFixed(1)},${y(predict(fit, 10 ** logDose)).toFixed(1)}`;
  }).join('');

  return (
    <svg
      className="chart dose-curve"
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
      {decades(low, high)
        .filter((e) => e >= low && e <= high)
        .map((e) => (
          <g key={e} className="axis">
            <line x1={x(e)} x2={x(e)} y1={y(bottom)} y2={y(bottom) + 5} />
            <text x={x(e)} y={y(bottom) + 18} textAnchor="middle">
              {m.number(10 ** e, e < 0 ? -e : 0)}
            </text>
          </g>
        ))}
      {hasZero && (
        <g className="axis">
          <text x={x(zero)} y={y(bottom) + 18} textAnchor="middle">
            0
          </text>
          <text className="axis-break" x={x(zero + 0.5)} y={y(bottom) + 4} textAnchor="middle">
            {'//'}
          </text>
        </g>
      )}
      <text className="axis-title" x={MARGIN.left + PLOT_W / 2} y={HEIGHT - 6} textAnchor="middle">
        {m.results.dose}
      </text>
      <text
        className="axis-title"
        transform={`translate(13 ${MARGIN.top + PLOT_H / 2}) rotate(-90)`}
        textAnchor="middle"
      >
        {label}
      </text>
      <path className="fit" d={path} />
      <line
        className="ec50"
        x1={x(Math.log10(fit.ec50))}
        x2={x(Math.log10(fit.ec50))}
        y1={y(bottom)}
        y2={y(predict(fit, fit.ec50))}
      />
      {points.map((p, i) => (
        <circle key={i} className="replicate" cx={xDose(p.dose)} cy={y(p.value)} r={3.5}>
          <title>{`${p.sampleName}: ${m.number(p.value, 1)}`}</title>
        </circle>
      ))}
    </svg>
  );
}
