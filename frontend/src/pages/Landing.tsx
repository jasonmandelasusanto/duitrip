import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { signInWithGoogle, signInWithEmailPassword, registerWithEmailPassword, friendlyAuthError } from '../services/auth';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';
import { Turnstile } from '../components/ui/Turnstile';

const TURNSTILE_SITE_KEY = import.meta.env.VITE_TURNSTILE_SITE_KEY || '';

type Tab = 'signin' | 'register';

export default function Landing() {
  const navigate = useNavigate();
  const [tab, setTab] = useState<Tab>('signin');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [name, setName] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [turnstileToken, setTurnstileToken] = useState<string | null>(null);

  const captchaRequired = !!TURNSTILE_SITE_KEY;
  const canSubmit = !loading && (!captchaRequired || !!turnstileToken);

  function switchTab(t: Tab) {
    setTab(t);
    setError(null);
  }

  async function handleEmailSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!canSubmit) return;
    setError(null);
    setLoading(true);
    try {
      if (tab === 'signin') {
        await signInWithEmailPassword(email, password);
        navigate('/dashboard');
      } else {
        if (!name.trim()) { setError('Please enter your name'); return; }
        await registerWithEmailPassword(email, password, name.trim());
        navigate('/onboarding');
      }
    } catch (err) {
      setError(friendlyAuthError(err));
    } finally {
      setLoading(false);
    }
  }

  async function handleGoogle() {
    setError(null);
    setLoading(true);
    try {
      await signInWithGoogle();
      navigate('/dashboard');
    } catch (err) {
      const msg = friendlyAuthError(err);
      if (!msg.includes('popup-closed')) setError(msg);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="min-h-screen bg-bg-base flex flex-col items-center justify-center p-6">
      <div className="w-full max-w-sm">
        <div className="text-center mb-8">
          <div className="text-5xl mb-3">✈️</div>
          <h1 className="text-3xl font-bold text-text-primary">Duitrip</h1>
          <p className="text-text-secondary text-sm mt-1">Track shared trip expenses</p>
          <p className="text-text-muted text-xs mt-1 max-w-xs mx-auto">
            Multi-currency splitting with exchange rates locked at time of recording.
          </p>
        </div>

        <div className="bg-bg-surface border border-bg-border rounded-2xl p-6">
          <div className="flex gap-1 bg-bg-elevated rounded-lg p-1 mb-5">
            {(['signin', 'register'] as Tab[]).map((t) => (
              <button
                key={t}
                onClick={() => switchTab(t)}
                className={`flex-1 py-1.5 rounded-md text-sm font-medium transition-colors ${
                  tab === t ? 'bg-bg-surface text-text-primary shadow-sm' : 'text-text-secondary hover:text-text-primary'
                }`}
              >
                {t === 'signin' ? 'Sign In' : 'Create Account'}
              </button>
            ))}
          </div>

          <form onSubmit={handleEmailSubmit} className="space-y-3">
            {tab === 'register' && (
              <Input
                label="Full Name"
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
                autoComplete="name"
                placeholder="Jane Smith"
              />
            )}
            <Input
              label="Email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              autoComplete="email"
              placeholder="you@example.com"
            />
            <Input
              label="Password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              autoComplete={tab === 'signin' ? 'current-password' : 'new-password'}
              placeholder="••••••••"
              minLength={6}
            />

            {captchaRequired && (
              <div className="flex justify-center pt-1">
                <Turnstile
                  siteKey={TURNSTILE_SITE_KEY}
                  onSuccess={(t) => setTurnstileToken(t)}
                  onExpired={() => setTurnstileToken(null)}
                />
              </div>
            )}

            {error && (
              <p className="text-sm text-danger bg-danger/10 rounded-lg px-3 py-2">{error}</p>
            )}

            <Button type="submit" className="w-full" disabled={!canSubmit}>
              {loading ? 'Please wait…' : tab === 'signin' ? 'Sign In' : 'Create Account'}
            </Button>
          </form>

          <div className="flex items-center gap-3 my-4">
            <hr className="flex-1 border-bg-border" />
            <span className="text-xs text-text-secondary">or</span>
            <hr className="flex-1 border-bg-border" />
          </div>

          <Button variant="ghost" className="w-full gap-2" onClick={handleGoogle} disabled={loading}>
            <img src="https://www.google.com/favicon.ico" alt="" className="w-4 h-4" />
            Continue with Google
          </Button>
        </div>
      </div>
    </div>
  );
}
