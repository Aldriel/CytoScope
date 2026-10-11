import { useMessages } from '../../i18n';

/** Round "?" button opening the user guide at the section of a tab. */
export function HelpButton({ onClick }: { onClick: () => void }) {
  const m = useMessages();
  return (
    <button type="button" className="help-button" title={m.tip.tabHelp} onClick={onClick}>
      ?
    </button>
  );
}
