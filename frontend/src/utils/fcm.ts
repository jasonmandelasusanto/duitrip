import { getMessaging, getToken } from 'firebase/messaging';
import { app } from '../services/firebase';
import api from '../services/api';

export async function registerFcmToken(): Promise<void> {
  const vapidKey = import.meta.env.VITE_FIREBASE_VAPID_KEY;
  if (!vapidKey) return; // Skip if not configured (local dev / emulator)

  try {
    if (!('Notification' in window)) return;
    const permission = await Notification.requestPermission();
    if (permission !== 'granted') return;

    const messaging = getMessaging(app);

    // Register the service worker with firebase config in query params so it can initialise
    const config = {
      apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
      authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
      projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
      storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET,
      messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID,
      appId: import.meta.env.VITE_FIREBASE_APP_ID,
    };
    const params = new URLSearchParams(config as Record<string, string>).toString();
    const swReg = await navigator.serviceWorker.register(
      `/firebase-messaging-sw.js?${params}`,
      { scope: '/' },
    );

    const token = await getToken(messaging, { vapidKey, serviceWorkerRegistration: swReg });
    if (token) {
      await api.patch('/users/me', { fcmToken: token });
    }
  } catch {
    // Gracefully degrade — push notifications are non-critical
  }
}
