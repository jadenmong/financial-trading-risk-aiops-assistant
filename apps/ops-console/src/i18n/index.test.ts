import { createPinia, setActivePinia } from 'pinia';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { DEFAULT_LOCALE, LOCALE_STORAGE_KEY, readStoredLocale, translateEnum } from './index.js';
import { useLocaleStore } from '../stores/locale.js';

function memoryStorage(initial: Record<string, string> = {}) {
  const values = new Map(Object.entries(initial));
  return { getItem: (key: string) => values.get(key) ?? null, setItem: (key: string, value: string) => values.set(key, value) };
}

afterEach(() => vi.unstubAllGlobals());

describe('console localization', () => {
  it('uses simplified Chinese when no stored locale exists', () => expect(readStoredLocale(memoryStorage())).toBe(DEFAULT_LOCALE));
  it('restores a valid stored locale', () => expect(readStoredLocale(memoryStorage({ [LOCALE_STORAGE_KEY]: 'en-US' }))).toBe('en-US'));
  it('switches to English and persists the selection', () => {
    const storage = memoryStorage();
    vi.stubGlobal('localStorage', storage);
    setActivePinia(createPinia());
    const locale = useLocaleStore();
    locale.setLocale('en-US');
    expect(locale.locale).toBe('en-US');
    expect(storage.getItem(LOCALE_STORAGE_KEY)).toBe('en-US');
  });
  it('falls back to the original value for unknown enum values', () => expect(translateEnum('zh-CN', 'severity', 'EMERGENCY')).toBe('EMERGENCY'));
});
