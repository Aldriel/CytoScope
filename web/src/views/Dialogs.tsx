import { version as reactVersion } from 'react';
import icon from '../assets/icon-64.png';
import { useMessages } from '../i18n';
import { Modal } from './common/Modal';
import { altShortcut, shortcut } from './keys';

export const APP_NAME = 'CytoScope';
const AUTHOR = 'Maxime Ethier';
const CONTACT_EMAIL = 'contact@maximeethier.com';

/** Name, version, author, data sources and disclaimer of the application. */
export function AboutDialog({ onClose }: { onClose: () => void }) {
  const m = useMessages();
  return (
    <Modal
      title={m.about.title(APP_NAME)}
      header={
        <>
          {APP_NAME}
          <br />
          {m.about.version(__APP_VERSION__)}
        </>
      }
      icon={<img src={icon} width={64} height={64} alt="" />}
      className="about-dialog"
      onClose={onClose}
      buttons={<button onClick={onClose}>{m.dialog.close}</button>}
    >
      <p>{m.about.description}</p>
      <p>
        <strong>{m.about.author(AUTHOR)}</strong>
        <br />
        <a href={m.about.website} target="_blank" rel="noopener noreferrer">
          {m.about.website}
        </a>
        <br />
        <a href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</a>
      </p>
      <p>{m.about.sources}</p>
      <p>{m.about.references}</p>
      <p className="muted small">{m.about.disclaimer}</p>
      <p className="muted small">{m.about.runtime(reactVersion)}</p>
    </Modal>
  );
}

/** Keyboard shortcuts of the application. */
export function ShortcutsDialog({ onClose }: { onClose: () => void }) {
  const m = useMessages();
  const rows: [string, string][] = [
    ['F1', m.menu.guide],
    [shortcut('O'), m.menu.open],
    [shortcut('S'), m.menu.report],
    [`${altShortcut('1')} … ${altShortcut('6')}`, m.shortcuts.tabs],
    [shortcut('='), m.menu.zoomIn],
    [shortcut('−'), m.menu.zoomOut],
    [shortcut('0'), m.menu.zoomReset],
    [shortcut('F'), m.shortcuts.helpSearch],
    ['Esc', m.shortcuts.escape],
  ];
  return (
    <Modal
      title={m.menu.shortcuts}
      onClose={onClose}
      buttons={<button onClick={onClose}>{m.dialog.close}</button>}
    >
      <table className="shortcuts">
        <thead>
          <tr>
            <th>{m.shortcuts.keys}</th>
            <th>{m.shortcuts.action}</th>
          </tr>
        </thead>
        <tbody>
          {rows.map(([keys, action]) => (
            <tr key={keys}>
              <td>
                <strong>{keys}</strong>
              </td>
              <td>{action}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </Modal>
  );
}
