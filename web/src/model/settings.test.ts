import { describe, expect, it } from 'vitest';
import { DEFAULT_FONT_SIZE, FONT_SIZE_KEY, LOCALE_KEY, UiSettings } from './settings';

/** Storage kept in memory. */
function memoryStorage(initial: Record<string, string> = {}) {
  const values = new Map(Object.entries(initial));
  return {
    values,
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => void values.set(key, value),
  };
}

describe('UiSettings', () => {
  it('starts in the language of the browser with the default text size', () => {
    const settings = new UiSettings(memoryStorage(), ['fr-CA']);

    expect(settings.locale.get()).toBe('fr');
    expect(settings.fontSize.get()).toBe(DEFAULT_FONT_SIZE);
  });

  it('restores the saved settings, keeping the text size in range', () => {
    const storage = memoryStorage({ [LOCALE_KEY]: 'en', [FONT_SIZE_KEY]: '99' });

    const settings = new UiSettings(storage, ['fr-CA']);

    expect(settings.locale.get()).toBe('en');
    expect(settings.fontSize.get()).toBe(24);
  });

  it('ignores settings that are not valid', () => {
    const storage = memoryStorage({ [LOCALE_KEY]: 'de', [FONT_SIZE_KEY]: 'large' });

    const settings = new UiSettings(storage, ['es']);

    expect(settings.locale.get()).toBe('en');
    expect(settings.fontSize.get()).toBe(DEFAULT_FONT_SIZE);
  });

  it('saves every change', () => {
    const storage = memoryStorage();
    const settings = new UiSettings(storage, ['en']);

    settings.setLocale('fr');
    settings.setFontSize(9);
    expect(settings.fontSize.get()).toBe(10);
    settings.increaseFontSize();
    settings.increaseFontSize();
    settings.decreaseFontSize();
    expect(settings.fontSize.get()).toBe(11);
    expect(storage.values.get(LOCALE_KEY)).toBe('fr');
    expect(storage.values.get(FONT_SIZE_KEY)).toBe('11');

    settings.resetFontSize();
    expect(storage.values.get(FONT_SIZE_KEY)).toBe(String(DEFAULT_FONT_SIZE));
  });
});
