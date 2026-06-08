import { ref } from "vue";
import { getNotifications, getUnreadCount, markRead, markAllRead } from "@/api/notification";
import { useWebSocket } from "@/composables/useWebSocket";
import { useUserStore } from "@/store/user";

const unreadCount = ref(0);
const notifications = ref<any[]>([]);
const loading = ref(false);

let wsInitialized = false;

export function useNotifications() {
  const { subscribe } = useWebSocket();
  const userStore = useUserStore();

  // Init WebSocket notification subscription once
  if (!wsInitialized) {
    wsInitialized = true;
    const userId = userStore.userInfo?.id;
    if (userId) {
      subscribe(`/topic/notifications/${userId}`, (payload: any) => {
        const exists = notifications.value.find((n: any) =>
          n.type === payload.type && n.relatedId === payload.relatedId
        );
        if (!exists) {
          notifications.value.unshift({
            ...payload,
            id: Date.now(),
            isRead: 0,
            createdAt: new Date().toLocaleString(),
          });
        }
        unreadCount.value++;
      });
    }
  }

  async function fetchUnreadCount() {
    try {
      const res = await getUnreadCount();
      unreadCount.value = res.data?.count || 0;
    } catch { /* ignore */ }
  }

  async function fetchNotifications() {
    loading.value = true;
    try {
      const res = await getNotifications(20);
      notifications.value = res.data || [];
    } catch { notifications.value = []; }
    finally { loading.value = false; }
  }

  async function handleMarkRead(id: number) {
    await markRead(id);
    const n = notifications.value.find((n: any) => n.id === id);
    if (n) n.isRead = 1;
    await fetchUnreadCount();
  }

  async function handleMarkAllRead() {
    await markAllRead();
    notifications.value.forEach((n: any) => { n.isRead = 1; });
    await fetchUnreadCount();
  }

  return {
    unreadCount,
    notifications,
    loading,
    fetchUnreadCount,
    fetchNotifications,
    handleMarkRead,
    handleMarkAllRead,
  };
}
