import { useStore } from '../controllers/useStore';
import { useMessages } from '../i18n';
import type { AppModel } from '../model/AppModel';

/**
 * Status bar at the bottom of the window: a spinner while a request runs, and the last message,
 * in red for an error. The message is translated again when the language changes.
 */
export function StatusBar({ model }: { model: AppModel }) {
  const m = useMessages();
  const status = useStore(model.status);
  const busy = useStore(model.busy);
  return (
    <div className="status-bar">
      <span className={busy ? 'spinner' : 'spinner idle'} aria-hidden="true" />
      <span className={status.error ? 'status-text error' : 'status-text'} role="status">
        {status.render(m)}
      </span>
    </div>
  );
}
