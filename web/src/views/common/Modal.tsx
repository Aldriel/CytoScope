import { useEffect, useId, useRef, type ReactNode } from 'react';

interface Props {
  title: string;
  /** Large text at the top of the dialog, below the title bar. */
  header?: ReactNode;
  /** Symbol shown next to the header, such as "!" for a warning. */
  icon?: ReactNode;
  children: ReactNode;
  /** Buttons at the bottom; the first one receives the focus. */
  buttons: ReactNode;
  /** Called on Escape or a click outside the dialog. */
  onClose: () => void;
  className?: string;
}

/**
 * Modal dialog in the style of a JavaFX Alert: title bar, optional header, content and buttons.
 * Escape and a click outside the dialog close it.
 */
export function Modal({ title, header, icon, children, buttons, onClose, className }: Props) {
  const titleId = useId();
  const dialog = useRef<HTMLDivElement>(null);

  const close = useRef(onClose);
  close.current = onClose;

  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    dialog.current?.querySelector<HTMLButtonElement>('.modal-buttons button')?.focus();
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.stopPropagation();
        close.current();
      }
    };
    window.addEventListener('keydown', onKey, true);
    return () => {
      window.removeEventListener('keydown', onKey, true);
      previous?.focus?.();
    };
  }, []);

  return (
    <div className="modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div
        className={`modal ${className ?? ''}`}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        ref={dialog}
      >
        <div className="modal-title" id={titleId}>
          {title}
        </div>
        {header && (
          <div className="modal-header">
            <div>{header}</div>
            {icon && <div className="modal-icon">{icon}</div>}
          </div>
        )}
        <div className="modal-content">{children}</div>
        <div className="modal-buttons">{buttons}</div>
      </div>
    </div>
  );
}
