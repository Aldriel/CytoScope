import { useEffect, useRef, useState, type KeyboardEvent as ReactKeyboardEvent } from 'react';

/** Item of a menu: a command, a sub-menu, or a line between groups of items. */
export type MenuItem =
  | {
      label: string;
      action: () => void;
      /** Keys shown on the right, such as "Ctrl+O". */
      shortcut?: string;
      /** Defined for a radio item: whether it is the chosen one. */
      checked?: boolean;
      disabled?: boolean;
    }
  | { label: string; items: MenuItem[] }
  | 'separator';

export interface Menu {
  label: string;
  items: MenuItem[];
}

/**
 * Menu bar of a desktop application: a click opens a menu, moving to another title while a menu
 * is open switches to it, sub-menus open when hovered, and Escape or a click elsewhere closes.
 */
export function MenuBar({ menus }: { menus: Menu[] }) {
  const [open, setOpen] = useState<number | null>(null);
  const bar = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (open === null) {
      return;
    }
    const close = (e: Event) => {
      if (!(e.target instanceof Node && bar.current?.contains(e.target))) {
        setOpen(null);
      }
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setOpen(null);
      } else if (e.key === 'ArrowLeft' || e.key === 'ArrowRight') {
        const delta = e.key === 'ArrowLeft' ? -1 : 1;
        setOpen((current) => ((current ?? 0) + delta + menus.length) % menus.length);
      }
    };
    window.addEventListener('mousedown', close);
    window.addEventListener('keydown', onKey);
    window.addEventListener('blur', close);
    return () => {
      window.removeEventListener('mousedown', close);
      window.removeEventListener('keydown', onKey);
      window.removeEventListener('blur', close);
    };
  }, [open, menus.length]);

  return (
    <div className="menu-bar" role="menubar" ref={bar}>
      {menus.map((menu, index) => (
        <div key={menu.label} className="menu">
          <button
            role="menuitem"
            aria-haspopup="true"
            aria-expanded={open === index}
            className={open === index ? 'menu-title open' : 'menu-title'}
            onMouseDown={(e) => {
              e.preventDefault();
              setOpen(open === index ? null : index);
            }}
            onKeyDown={(e) => {
              if (e.key === 'Enter' || e.key === ' ' || e.key === 'ArrowDown') {
                e.preventDefault();
                setOpen(index);
                requestAnimationFrame(() =>
                  bar.current
                    ?.querySelector<HTMLButtonElement>('.menu-list button:not(:disabled)')
                    ?.focus(),
                );
              }
            }}
            onMouseEnter={() => open !== null && setOpen(index)}
          >
            {menu.label}
          </button>
          {open === index && <MenuList items={menu.items} onDone={() => setOpen(null)} />}
        </div>
      ))}
    </div>
  );
}

/** Drop-down list of a menu or sub-menu. */
function MenuList({ items, onDone }: { items: MenuItem[]; onDone: () => void }) {
  const [submenu, setSubmenu] = useState<number | null>(null);
  const list = useRef<HTMLUListElement>(null);

  // Arrow keys move between the items, like a native menu.
  const onKeyDown = (e: ReactKeyboardEvent) => {
    if (e.key !== 'ArrowDown' && e.key !== 'ArrowUp') {
      return;
    }
    e.preventDefault();
    e.stopPropagation();
    const buttons = [
      ...(list.current?.querySelectorAll<HTMLButtonElement>(
        ':scope > li > button:not(:disabled)',
      ) ?? []),
    ];
    const current = buttons.indexOf(document.activeElement as HTMLButtonElement);
    const next = e.key === 'ArrowDown' ? current + 1 : current - 1;
    buttons[(next + buttons.length) % buttons.length]?.focus();
  };

  return (
    <ul className="menu-list" role="menu" ref={list} onKeyDown={onKeyDown}>
      {items.map((item, index) => {
        if (item === 'separator') {
          return <li key={index} role="separator" className="separator" />;
        }
        if ('items' in item) {
          return (
            <li
              key={index}
              role="none"
              className="has-submenu"
              onMouseEnter={() => setSubmenu(index)}
              onMouseLeave={() => setSubmenu(null)}
            >
              <button
                role="menuitem"
                aria-haspopup="true"
                aria-expanded={submenu === index}
                onClick={() => setSubmenu(index)}
                onKeyDown={(e) => {
                  if (e.key === 'ArrowRight' || e.key === 'Enter') {
                    e.preventDefault();
                    e.stopPropagation();
                    setSubmenu(index);
                    const parent = e.currentTarget.parentElement;
                    requestAnimationFrame(() =>
                      parent
                        ?.querySelector<HTMLButtonElement>(':scope > .menu-list button')
                        ?.focus(),
                    );
                  }
                }}
              >
                <span className="menu-check" />
                <span className="menu-label">{item.label}</span>
                <span className="menu-arrow">▶</span>
              </button>
              {submenu === index && <MenuList items={item.items} onDone={onDone} />}
            </li>
          );
        }
        return (
          <li key={index} role="none">
            <button
              role={item.checked === undefined ? 'menuitem' : 'menuitemradio'}
              aria-checked={item.checked}
              disabled={item.disabled}
              onMouseEnter={() => setSubmenu(null)}
              onClick={() => {
                onDone();
                item.action();
              }}
            >
              <span className="menu-check">{item.checked ? '●' : ''}</span>
              <span className="menu-label">{item.label}</span>
              {item.shortcut && <span className="menu-shortcut">{item.shortcut}</span>}
            </button>
          </li>
        );
      })}
    </ul>
  );
}
