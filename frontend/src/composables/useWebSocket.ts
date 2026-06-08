import { ref, onUnmounted } from "vue";
import SockJS from "sockjs-client";
import { Client, IFrame } from "@stomp/stompjs";
import { useRouter } from "vue-router";

const connected = ref(false);
let stompClient: Client | null = null;
const subscriptions: Map<string, any> = new Map();

export function useWebSocket() {
  const router = useRouter();

  function connect() {
    if (stompClient?.active) return;

    const token = localStorage.getItem("token");
    if (!token) return;

    return new Promise<void>((resolve) => {
      const socket = new SockJS("/api/ws");
      stompClient = new Client({
        webSocketFactory: () => socket as any,
        connectHeaders: {
          Authorization: `Bearer ${token}`,
        },
        debug: () => {}, // silent
        reconnectDelay: 5000,
        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000,
        onConnect: (frame: IFrame) => {
          connected.value = true;
          resolve();
        },
        onStompError: (frame: IFrame) => {
          console.error("STOMP error:", frame.headers["message"]);
          connected.value = false;
          if (frame.headers["message"]?.includes("401") || frame.headers["message"]?.includes("403")) {
            router.push("/login");
          }
          resolve();
        },
        onWebSocketClose: () => {
          connected.value = false;
        },
      });
      stompClient.activate();
    });
  }

  function subscribe(destination: string, callback: (payload: any) => void) {
    if (!stompClient?.active) return;
    if (subscriptions.has(destination)) return;

    const sub = stompClient.subscribe(destination, (message) => {
      try {
        const body = JSON.parse(message.body);
        callback(body);
      } catch (e) {
        console.error("Failed to parse WebSocket message:", e);
      }
    });
    subscriptions.set(destination, sub);
  }

  function unsubscribe(destination: string) {
    const sub = subscriptions.get(destination);
    if (sub) {
      sub.unsubscribe();
      subscriptions.delete(destination);
    }
  }

  function disconnect() {
    subscriptions.forEach((sub) => sub.unsubscribe());
    subscriptions.clear();
    if (stompClient?.active) {
      stompClient.deactivate();
    }
    stompClient = null;
    connected.value = false;
  }

  onUnmounted(() => {
    disconnect();
  });

  return {
    connected,
    connect,
    subscribe,
    unsubscribe,
    disconnect,
  };
}
