import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import api from '../services/api';
import { useAppStore } from '../store/useAppStore';
import { Button } from '../components/ui/Button';

export default function InviteAccept() {
  const { tripId } = useParams<{ tripId: string }>();
  const { user } = useAppStore();
  const navigate = useNavigate();
  const [status, setStatus] = useState<'idle' | 'loading' | 'success' | 'error'>('idle');
  const [message, setMessage] = useState('');

  async function acceptInvite() {
    if (!tripId) return;
    setStatus('loading');
    try {
      await api.post(`/trips/${tripId}/invites/accept`);
      setStatus('success');
      setTimeout(() => navigate(`/trips/${tripId}`), 1500);
    } catch (e: unknown) {
      setStatus('error');
      setMessage((e as { response?: { data?: { detail?: string } } })?.response?.data?.detail || 'Failed to accept invite');
    }
  }

  if (!user) return (
    <div className="min-h-screen bg-bg-base flex items-center justify-center p-6">
      <p className="text-text-muted">Please sign in to accept this invite.</p>
    </div>
  );

  return (
    <div className="min-h-screen bg-bg-base flex items-center justify-center p-6">
      <div className="max-w-sm w-full text-center">
        <p className="text-5xl mb-6">✈️</p>
        <h1 className="text-xl font-bold text-text-primary mb-2">You've been invited!</h1>
        <p className="text-text-secondary text-sm mb-6">Join the trip and start tracking expenses together.</p>
        {status === 'success' && <p className="text-success mb-4">✓ Joined! Redirecting…</p>}
        {status === 'error' && <p className="text-danger mb-4">{message}</p>}
        <Button size="lg" className="w-full" onClick={acceptInvite} disabled={status === 'loading' || status === 'success'}>
          {status === 'loading' ? 'Joining…' : 'Accept Invite'}
        </Button>
      </div>
    </div>
  );
}
