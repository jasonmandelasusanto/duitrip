import { useEffect, useRef, useState } from 'react';
import api from '../../services/api';
import { formatCurrency } from '../../utils/currency';
import type { Notification } from '../../types';

function timeAgo(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime();
  const m = Math.floor(diff / 60000);
  if (m < 1) return 'just now';
  if (m < 60) return `${m}m ago`;
  const h = Math.floor(m / 60);
  if (h < 24) return `${h}h ago`;
  return `${Math.floor(h / 24)}d ago`;
}

export function NotificationBell() {
  const [open, setOpen] = useState(false);
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const ref = useRef<HTMLDivElement>(null);

  const unread = notifications.filter((n) => !n.read).length;

  async function fetchNotifications() {
    try {
      const res = await api.get('/notifications');
      setNotifications(res.data);
    } catch { /* silent */ }
  }

  useEffect(() => { fetchNotifications(); }, []);

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    }
    if (open) document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [open]);

  function toggle() {
    if (!open) {
      fetchNotifications();
      if (unread > 0) {
        api.post('/notifications/read-all').catch(() => {});
        setNotifications((prev) => prev.map((n) => ({ ...n, read: true })));
      }
    }
    setOpen((o) => !o);
  }

  async function dismiss(notifId: string) {
    await api.delete(`/notifications/${notifId}`).catch(() => {});
    setNotifications((prev) => prev.filter((n) => n.notifId !== notifId));
  }

  return (
    <div className="relative" ref={ref}>
      <button
        onClick={toggle}
        className="relative p-2 rounded-lg hover:bg-bg-surface transition-colors text-text-secondary hover:text-text-primary"
        title="Notifications"
      >
        <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
          <path d="M13.73 21a2 2 0 0 1-3.46 0"/>
        </svg>
        {unread > 0 && (
          <span className="absolute top-1 right-1 w-4 h-4 bg-danger rounded-full flex items-center justify-center text-white text-[9px] font-bold leading-none">
            {unread > 9 ? '9+' : unread}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 mt-1 w-80 max-h-96 overflow-y-auto bg-bg-surface border border-bg-border rounded-xl shadow-2xl z-50">
          <div className="flex items-center justify-between px-4 py-3 border-b border-bg-border">
            <p className="text-sm font-semibold text-text-primary">Notifications</p>
          </div>

          {notifications.length === 0 ? (
            <p className="text-sm text-text-muted text-center py-8">No notifications</p>
          ) : (
            <div className="divide-y divide-bg-border">
              {notifications.map((n) => (
                <div key={n.notifId} className={`px-4 py-3 flex items-start gap-3 ${!n.read ? 'bg-teal/5' : ''}`}>
                  <span className="text-base mt-0.5">🔔</span>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm text-text-primary leading-snug">
                      <span className="font-medium">{n.fromName}</span> reminded you to settle{' '}
                      <span className="font-semibold text-amber">{formatCurrency(n.amount, n.currency)}</span>{' '}
                      for <span className="font-medium">{n.tripName}</span>
                    </p>
                    <p className="text-xs text-text-muted mt-1">{timeAgo(n.createdAt)}</p>
                  </div>
                  <button
                    onClick={() => dismiss(n.notifId)}
                    className="text-text-muted hover:text-danger transition-colors shrink-0 mt-0.5"
                    title="Dismiss"
                  >
                    <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                      <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
                    </svg>
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
