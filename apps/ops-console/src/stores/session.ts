import { defineStore } from 'pinia';
import { ref } from 'vue';

/** Access tokens intentionally live only in memory. */
export const useSessionStore = defineStore('session', () => {
  const accessToken = ref<string>();
  const subject = ref<string>();
  function establish(token: string, sub: string) { accessToken.value = token; subject.value = sub; }
  function clear() { accessToken.value = undefined; subject.value = undefined; }
  return { accessToken, subject, establish, clear };
});
