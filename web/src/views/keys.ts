/** Whether the browser runs on a Mac, where shortcuts use the Command key. */
const MAC = typeof navigator !== 'undefined' && /Mac|iPhone|iPad/.test(navigator.platform);

/**
 * Writes a shortcut as the platform shows it: "Ctrl+O" on Windows and Linux, "⌘O" on a Mac.
 *
 * @param key key pressed with Ctrl (Command on a Mac)
 */
export const shortcut = (key: string) => (MAC ? `⌘${key}` : `Ctrl+${key}`);

/** Writes a shortcut using Alt (Option on a Mac). */
export const altShortcut = (key: string) => (MAC ? `⌥${key}` : `Alt+${key}`);

/** Tells whether the Ctrl key (Command on a Mac) is held, without Alt. */
export const isShortcut = (e: KeyboardEvent) => (MAC ? e.metaKey : e.ctrlKey) && !e.altKey;
