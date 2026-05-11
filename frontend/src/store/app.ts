import { defineStore } from "pinia";
import { ref } from "vue";

export const useAppStore = defineStore("app", () => {
  const sidebarCollapsed = ref(false);
  const currentProjectId = ref<number | null>(null);
  const currentProjectName = ref("");

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value;
  }

  function setCurrentProject(id: number, name: string) {
    currentProjectId.value = id;
    currentProjectName.value = name;
  }

  return {
    sidebarCollapsed,
    currentProjectId,
    currentProjectName,
    toggleSidebar,
    setCurrentProject,
  };
});
