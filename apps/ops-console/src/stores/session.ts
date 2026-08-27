import { defineStore } from 'pinia';
import { ref } from 'vue';

/** Access tokens intentionally live only in memory. */
export const useSessionStore = defineStore('session', () => {
  const accessToken = ref<string>();
  const idToken = ref<string>();
  const subject = ref<string>();
  function establish(access: string, sub: string, id?: string) {
    accessToken.value = access;
    idToken.value = id;
    subject.value = sub;
  }
  function clear() {
    accessToken.value = undefined;
    idToken.value = undefined;
    subject.value = undefined;
  }
  return { accessToken, idToken, subject, establish, clear };
});
