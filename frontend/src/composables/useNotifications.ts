import { ref, onMounted, onUnmounted } from "vue";
import { getNotifications, getUnreadCount, markRead, markAllRead } from "@/api/notification";

export function useNotifications() {
  const unreadCount = ref(0);
  const notifications = ref<any[]>([]);
  const loading = ref(false);

  let timer: ReturnType<typeof setInterval> | null = null;

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
    const n = notifications.value.find(n => n.id === id);
    if (n) n.isRead = 1;
    await fetchUnreadCount();
  }

  async function handleMarkAllRead() {
    await markAllRead();
    notifications.value.forEach(n => { n.isRead = 1; });
    await fetchUnreadCount();
  }

  onMounted(() => {
    fetchUnreadCount();
    timer = setInterval(fetchUnreadCount, 30000);
  });

  onUnmounted(() => {
    if (timer) clearInterval(timer);
  });

  return {
    unreadCount,
    notifications,
    loading,
    fetchNotifications,
    handleMarkRead,
    handleMarkAllRead,
  };
}
