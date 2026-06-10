import { ref, onUnmounted } from "vue";
import SockJS from "sockjs-client";
import { Client, IFrame } from "@stomp/stompjs";
import { useRouter } from "vue-router";

const connected = ref(false);
let stompClient: Client | null = null;
const subscriptions: Map<string, any> = new Map();

// 待连接后自动订阅的队列
interface PendingSub {
  destination: string;
  callback: (payload: any) => void;
}
let pendingSubs: PendingSub[] = [];
let connectPromise: Promise<void> | null = null;

export function useWebSocket() {
  const router = useRouter();

  function connect() {
    if (stompClient?.active) return Promise.resolve();
    if (connectPromise) return connectPromise; // 防止重复连接

    const token = localStorage.getItem("token");
    if (!token) return Promise.resolve();

    connectPromise = new Promise<void>((resolve) => {
      try {
        const socket = new SockJS("/api/ws");
        stompClient = new Client({
          webSocketFactory: () => socket as any,
          connectHeaders: {
            Authorization: `Bearer ${token}`,
          },
          debug: () => {},
          reconnectDelay: 5000,
          heartbeatIncoming: 10000,
          heartbeatOutgoing: 10000,
          onConnect: (_frame: IFrame) => {
            connected.value = true;
            // 连接成功后，执行队列中的待订阅
            const queue = pendingSubs.splice(0);
            for (const sub of queue) {
              doSubscribe(sub.destination, sub.callback);
            }
            resolve();
          },
          onStompError: (frame: IFrame) => {
            console.warn("[WS] STOMP error:", frame.headers["message"]);
            connected.value = false;
            resolve(); // 即使错误也 resolve，避免永久挂起
          },
          onWebSocketClose: () => {
            connected.value = false;
          },
          onDisconnect: () => {
            connected.value = false;
          },
        });
        stompClient.activate();
      } catch (e) {
        console.error("[WS] Failed to create STOMP client:", e);
        resolve();
      }
    });

    return connectPromise;
  }

  /** 实际执行订阅（仅在已连接状态下调用） */
  function doSubscribe(destination: string, callback: (payload: any) => void) {
    if (!stompClient || !stompClient.active) return;
    if (subscriptions.has(destination)) return;

    try {
      const sub = stompClient.subscribe(destination, (message) => {
        try {
          const body = JSON.parse(message.body);
          callback(body);
        } catch (e) {
          // 忽略解析失败的消息
        }
      });
      subscriptions.set(destination, sub);
    } catch (e) {
      console.warn("[WS] Subscribe failed:", destination, e);
    }
  }

  /**
   * 订阅主题 — 自动处理连接状态：
   * - 已连接：直接订阅
   * - 未连接：先自动连接，连上后再订阅
   */
  function subscribe(destination: string, callback: (payload: any) => void) {
    if (connected.value && stompClient?.active) {
      doSubscribe(destination, callback);
    } else {
      // 未连接时加入队列，connect 成功后会自动消费
      pendingSubs.push({ destination, callback });
      // 触发连接（如果还没开始）
      connect();
    }
  }

  function unsubscribe(destination: string) {
    const sub = subscriptions.get(destination);
    if (sub) {
      try { sub.unsubscribe(); } catch {}
      subscriptions.delete(destination);
    }
    // 同时从待订阅队列中移除
    pendingSubs = pendingSubs.filter((p) => p.destination !== destination);
  }

  function disconnect() {
    pendingSubs = [];
    subscriptions.forEach((sub) => { try { sub.unsubscribe(); } catch {} });
    subscriptions.clear();
    if (stompClient?.active) {
      stompClient.deactivate();
    }
    stompClient = null;
    connected.value = false;
    connectPromise = null;
  }

  onUnmounted(() => {
    // 注意：不在这里 disconnect，因为 useWebSocket 是单例模块
    // 如果每个组件都 disconnect，其他组件的订阅会丢失
  });

  return {
    connected,
    connect,
    subscribe,
    unsubscribe,
    disconnect,
  };
}
