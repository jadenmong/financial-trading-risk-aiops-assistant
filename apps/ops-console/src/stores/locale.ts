import { defineStore } from 'pinia';
import { ref } from 'vue';
import { readStoredLocale, saveLocale, type Locale } from '../i18n/index.js';

export const useLocaleStore = defineStore('locale', () => {
  const locale = ref<Locale>(readStoredLocale());
  function setLocale(nextLocale: Locale) {
    locale.value = nextLocale;
    saveLocale(nextLocale);
  }
  return { locale, setLocale };
});
