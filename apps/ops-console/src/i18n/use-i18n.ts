import { storeToRefs } from 'pinia';
import { translate, translateEnum, type EnumGroup, type MessageKey } from './index.js';
import { useLocaleStore } from '../stores/locale.js';

export function useI18n() {
  const localeStore = useLocaleStore();
  const { locale } = storeToRefs(localeStore);
  return {
    locale,
    setLocale: localeStore.setLocale,
    t: (key: MessageKey) => translate(locale.value, key),
    enumText: (group: EnumGroup, value: unknown) => translateEnum(locale.value, group, value),
  };
}
