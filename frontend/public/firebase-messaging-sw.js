importScripts('https://www.gstatic.com/firebasejs/10.12.0/firebase-app-compat.js');
importScripts('https://www.gstatic.com/firebasejs/10.12.0/firebase-messaging-compat.js');

// Config is injected at SW registration time via URL params for dev flexibility.
// In production, hardcode or inject via your build pipeline.
const url = new URL(location.href);
const firebaseConfig = {
  apiKey: url.searchParams.get('apiKey') || '',
  authDomain: url.searchParams.get('authDomain') || '',
  projectId: url.searchParams.get('projectId') || '',
  storageBucket: url.searchParams.get('storageBucket') || '',
  messagingSenderId: url.searchParams.get('messagingSenderId') || '',
  appId: url.searchParams.get('appId') || '',
};

firebase.initializeApp(firebaseConfig);
const messaging = firebase.messaging();

messaging.onBackgroundMessage((payload) => {
  const { title, body } = payload.notification || {};
  if (!title) return;
  self.registration.showNotification(title, {
    body: body || '',
    icon: '/icon-192.png',
    badge: '/icon-192.png',
  });
});
