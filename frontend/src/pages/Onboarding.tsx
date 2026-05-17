import { useCallback, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../services/api';
import { useAppStore } from '../store/useAppStore';
import { Input } from '../components/ui/Input';
import { Button } from '../components/ui/Button';
import { Turnstile } from '../components/ui/Turnstile';

const TURNSTILE_SITE_KEY = import.meta.env.VITE_TURNSTILE_SITE_KEY || '';

const POPULAR = ['USD', 'EUR', 'SGD', 'IDR', 'THB', 'MYR', 'JPY', 'AUD', 'GBP', 'HKD'];

export default function Onboarding() {
  const { user, setUser } = useAppStore();
  const navigate = useNavigate();
  const [currency, setCurrency] = useState('USD');
  const [loading, setLoading] = useState(false);
  const [turnstileToken, setTurnstileToken] = useState<string | null>(null);

  const captchaRequired = !!TURNSTILE_SITE_KEY;
  const handleTurnstileSuccess = useCallback((token: string) => setTurnstileToken(token), []);

  async function handleSave() {
    setLoading(true);
    try {
      const headers = turnstileToken ? { 'X-Turnstile-Token': turnstileToken } : {};
      await api.post('/users/me/init', {
        uid: user?.uid,
        email: user?.email,
        displayName: user?.displayName,
        homeCurrency: currency,
      }, { headers });
      setUser({ ...user!, homeCurrency: currency });
      navigate('/dashboard');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="min-h-screen bg-bg-base flex flex-col items-center justify-center p-6">
      <div className="w-full max-w-sm">
        <h1 className="text-2xl font-bold text-text-primary mb-1">Welcome, {user?.displayName}!</h1>
        <p className="text-text-secondary text-sm mb-6">What's your home currency?</p>

        <div className="grid grid-cols-5 gap-2 mb-4">
          {POPULAR.map((c) => (
            <button
              key={c}
              onClick={() => setCurrency(c)}
              className={`py-2 rounded-xl text-sm font-medium transition-colors ${
                currency === c
                  ? 'bg-teal text-white'
                  : 'bg-bg-surface border border-bg-border text-text-secondary hover:border-teal/50'
              }`}
            >
              {c}
            </button>
          ))}
        </div>

        <Input label="Or type a currency code" value={currency} onChange={(e) => setCurrency(e.target.value.toUpperCase())} maxLength={3} className="mb-4" />

        {captchaRequired && (
          <div className="flex justify-center mb-4">
            <Turnstile siteKey={TURNSTILE_SITE_KEY} onSuccess={handleTurnstileSuccess} />
          </div>
        )}

        <Button className="w-full" size="lg" onClick={handleSave} disabled={loading || !currency || (captchaRequired && !turnstileToken)}>
          {loading ? 'Saving…' : 'Continue'}
        </Button>
      </div>
    </div>
  );
}
