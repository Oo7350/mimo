import { defineStore } from "pinia";
import { ref } from "vue";

const STORAGE_KEY = "mimo-theme";

function applyTheme(dark: boolean) {
  document.documentElement.setAttribute("data-theme", dark ? "dark" : "light");
  localStorage.setItem(STORAGE_KEY, dark ? "dark" : "light");
}

export const useAppStore = defineStore("app", () => {
  const sidebarCollapsed = ref(false);
  const currentProjectId = ref<number | null>(null);
  const currentProjectName = ref("");
  const darkMode = ref(localStorage.getItem(STORAGE_KEY) === "dark");

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value;
  }

  function setCurrentProject(id: number, name: string) {
    currentProjectId.value = id;
    currentProjectName.value = name;
  }

  function toggleDarkMode() {
    darkMode.value = !darkMode.value;
    applyTheme(darkMode.value);
  }

  function initTheme() {
    applyTheme(darkMode.value);
  }

  return {
    sidebarCollapsed,
    currentProjectId,
    currentProjectName,
    darkMode,
    toggleSidebar,
    setCurrentProject,
    toggleDarkMode,
    initTheme,
  };
});
