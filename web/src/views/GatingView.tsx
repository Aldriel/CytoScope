import { useState } from 'react';
import type { GatingController } from '../controllers/useGatingController';
import { download } from '../controllers/navigator';
import { useMessages } from '../i18n';
import { ROOT_ID } from '../model/gating';
import { SCALE_TYPES, type ScaleType } from '../model/types';
import { HelpButton } from './common/HelpButton';
import { FlowPlot, TOOLS, type Tool } from './plot/FlowPlot';

interface Props {
  ctl: GatingController;
  onHelp: () => void;
}

/**
 * "Gating" tab: the hierarchy of the populations on the left; on the right, the plot of the
 * population shown, on which the gates of its children are drawn and moved.
 */
export function GatingView({ ctl, onHelp }: Props) {
  const m = useMessages();
  const [renaming, setRenaming] = useState<{ id: string; name: string } | null>(null);
  const { workspace, plot, axes, population } = ctl;

  if (workspace === null || workspace.samples.length === 0 || population === null) {
    return (
      <div className="tab-content">
        <div className="row">
          <p className="placeholder grow">{m.gating.needsTubes}</p>
          <HelpButton onClick={onHelp} />
        </div>
      </div>
    );
  }

  const histogram = axes.y === null;
  const unavailable = (tool: Tool) =>
    (tool === 'interval' && !histogram) || (histogram && tool !== 'interval' && tool !== 'select');
  const scaleOf = (axis: 'x' | 'y') => (axis === 'x' ? plot?.x.scale : plot?.y?.scale);

  const commitRename = () => {
    if (renaming) {
      ctl.rename(renaming.id, renaming.name);
      setRenaming(null);
    }
  };

  const exportPopulation = (id: string) => {
    const url = ctl.exportUrl(id);
    if (url !== null) {
      download(url);
    }
  };

  return (
    <div className="tab-content gating">
      <aside className="hierarchy">
        <h3>{m.gating.hierarchy}</h3>
        <ul role="tree" aria-label={m.gating.hierarchy}>
          {ctl.populations.map(({ population: p, depth }) => {
            const stats = ctl.stats.get(p.id);
            const selected = p.id === population.id;
            return (
              <li
                key={p.id}
                role="treeitem"
                aria-selected={selected}
                className={selected ? 'selected' : undefined}
                style={{ paddingLeft: `${0.4 + depth * 1.1}em` }}
              >
                {renaming?.id === p.id ? (
                  <input
                    autoFocus
                    aria-label={m.gating.renamePrompt}
                    value={renaming.name}
                    onChange={(e) => setRenaming({ id: p.id, name: e.target.value })}
                    onBlur={commitRename}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') {
                        commitRename();
                      } else if (e.key === 'Escape') {
                        setRenaming(null);
                      }
                    }}
                  />
                ) : (
                  <button type="button" className="population" onClick={() => ctl.open(p.id)}>
                    <span className="name">{p.name}</span>
                    {stats && (
                      <span className="muted">
                        {p.id === ROOT_ID ? m.count(stats.count) : m.percent(stats.percentOfParent)}
                      </span>
                    )}
                  </button>
                )}
                {selected && (
                  <span className="actions">
                    {p.id !== ROOT_ID && (
                      <>
                        <button
                          type="button"
                          className="link"
                          onClick={() => setRenaming({ id: p.id, name: p.name })}
                        >
                          {m.gating.rename}
                        </button>
                        <button type="button" className="link" onClick={() => ctl.remove(p.id)}>
                          {m.gating.remove}
                        </button>
                      </>
                    )}
                    <button
                      type="button"
                      className="link"
                      title={m.tip.export}
                      onClick={() => exportPopulation(p.id)}
                    >
                      {m.gating.export}
                    </button>
                  </span>
                )}
              </li>
            );
          })}
        </ul>
        <p className="muted small">{m.gating.shared}</p>
      </aside>

      <section className="plot-panel">
        <div className="row wrap">
          <label htmlFor="gating-tube">{m.gating.tube}</label>
          <select
            id="gating-tube"
            value={ctl.sampleId ?? ''}
            onChange={(e) => ctl.selectSample(e.target.value)}
          >
            {workspace.samples.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </select>
          <span className="grow" />
          <HelpButton onClick={onHelp} />
        </div>
        <div className="row wrap">
          {(['x', 'y'] as const).map((axis) => {
            const channel = axes[axis];
            return (
              <span key={axis} className="axis-choice">
                <label htmlFor={`gating-${axis}`}>{m.gating[axis]}</label>
                <select
                  id={`gating-${axis}`}
                  value={channel ?? ''}
                  onChange={(e) => ctl.setAxis(axis, e.target.value === '' ? null : e.target.value)}
                >
                  {axis === 'y' && <option value="">{m.gating.histogram}</option>}
                  {ctl.channels.map((c) => (
                    <option key={c.name} value={c.name}>
                      {c.displayName}
                    </option>
                  ))}
                </select>
                {channel !== null && (
                  <select
                    aria-label={`${m.gating.scale} ${m.gating[axis]}`}
                    title={m.tip.scale}
                    value={scaleOf(axis) ?? ''}
                    onChange={(e) => ctl.setScale(channel, e.target.value as ScaleType)}
                  >
                    {SCALE_TYPES.map((s) => (
                      <option key={s} value={s}>
                        {m.scale[s]}
                      </option>
                    ))}
                  </select>
                )}
              </span>
            );
          })}
        </div>
        <div className="row wrap tools" role="toolbar" aria-label={m.gating.tools}>
          {TOOLS.map((tool) => (
            <button
              key={tool}
              type="button"
              aria-pressed={ctl.tool === tool}
              className={ctl.tool === tool ? 'tool active' : 'tool'}
              disabled={unavailable(tool)}
              title={
                unavailable(tool)
                  ? tool === 'interval'
                    ? m.gating.needsHistogram
                    : m.gating.needsDotPlot
                  : m.gating.toolTip[tool]
              }
              onClick={() => ctl.setTool(tool)}
            >
              {m.gating.tool[tool]}
            </button>
          ))}
          <span className="muted small">{m.gating.toolTip[ctl.tool]}</span>
        </div>
        <div className="plot-frame">
          {plot && (
            <>
              <FlowPlot
                plot={plot}
                gates={ctl.gates}
                tool={unavailable(ctl.tool) ? 'select' : ctl.tool}
                label={m.gating.plotLabel(plot.x.displayName, plot.y?.displayName ?? null)}
                onCreate={ctl.create}
                onMove={ctl.move}
                onOpen={ctl.open}
              />
              <p className="muted small">
                {population.name} · {m.gating.events(plot.events)}
              </p>
            </>
          )}
        </div>
      </section>
    </div>
  );
}
