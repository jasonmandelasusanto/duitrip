import { useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../services/api';
import { useAppStore } from '../store/useAppStore';
import { Input } from '../components/ui/Input';
import { Button } from '../components/ui/Button';
import { Avatar } from '../components/ui/Avatar';
import KofiWidget from '../components/ui/KofiWidget';
import { signOut } from '../services/auth';
import { uploadProfilePicture } from '../utils/imageUpload';

const POPULAR = ['USD', 'EUR', 'SGD', 'IDR', 'THB', 'MYR', 'JPY', 'AUD', 'GBP', 'HKD'];
const MAX_BYTES = 5 * 1024 * 1024;

export default function Profile() {
  const { user, setUser } = useAppStore();
  const navigate = useNavigate();
  const [displayName, setDisplayName] = useState(user?.displayName || '');
  const [currency, setCurrency] = useState(user?.homeCurrency || 'USD');
  const [loading, setLoading] = useState(false);
  const [photoLoading, setPhotoLoading] = useState(false);
  const [photoError, setPhotoError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  async function handleSave() {
    setLoading(true);
    try {
      await api.patch('/users/me', { displayName: displayName.trim() || undefined, homeCurrency: currency });
      setUser({ ...user!, displayName: displayName.trim() || user!.displayName, homeCurrency: currency });
      navigate('/dashboard');
    } finally { setLoading(false); }
  }

  async function handlePhotoChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    setPhotoError(null);
    if (file.size > MAX_BYTES) {
      setPhotoError('Image must be under 5 MB');
      return;
    }
    setPhotoLoading(true);
    try {
      const dataUrl = await uploadProfilePicture(file);
      await api.patch('/users/me', { photoURL: dataUrl });
      setUser({ ...user!, photoURL: dataUrl });
    } catch {
      setPhotoError('Failed to upload photo');
    } finally {
      setPhotoLoading(false);
      e.target.value = '';
    }
  }

  return (
    <div className="min-h-screen bg-bg-base">
      <div className="max-w-lg mx-auto px-4 pt-8 pb-10">
        <button onClick={() => navigate(-1)} className="text-text-secondary text-sm mb-6">← Back</button>
        <h1 className="text-2xl font-bold text-text-primary mb-6">Profile</h1>

        <div className="bg-bg-surface border border-bg-border rounded-2xl p-4 mb-6 flex items-center gap-4">
          <button
            onClick={() => fileInputRef.current?.click()}
            className="relative group flex-shrink-0"
            disabled={photoLoading}
            title="Change photo"
          >
            <Avatar src={user?.photoURL} name={displayName || user?.displayName || ''} size="lg" />
            <span className="absolute inset-0 rounded-full bg-black/40 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity text-white text-xs">
              {photoLoading ? '…' : 'Edit'}
            </span>
          </button>
          <div className="flex-1 min-w-0">
            <p className="font-semibold text-text-primary truncate">{displayName || user?.displayName}</p>
            <p className="text-sm text-text-secondary truncate">{user?.email}</p>
            {photoError && <p className="text-xs text-danger mt-1">{photoError}</p>}
          </div>
          <input ref={fileInputRef} type="file" accept="image/*" className="hidden" onChange={handlePhotoChange} />
        </div>

        <Input
          label="Display Name"
          value={displayName}
          onChange={(e) => setDisplayName(e.target.value)}
          placeholder="Your name"
          className="mb-4"
        />

        <p className="text-sm text-text-secondary mb-3">Home Currency</p>
        <div className="grid grid-cols-5 gap-2 mb-4">
          {POPULAR.map((c) => (
            <button key={c} onClick={() => setCurrency(c)}
              className={`py-2 rounded-xl text-sm font-medium transition-colors ${
                currency === c ? 'bg-teal text-white' : 'bg-bg-surface border border-bg-border text-text-secondary hover:border-teal/50'
              }`}
            >{c}</button>
          ))}
        </div>
        <Input label="Custom currency code" value={currency} onChange={(e) => setCurrency(e.target.value.toUpperCase())} maxLength={3} className="mb-6" />

        <Button className="w-full mb-3" onClick={handleSave} disabled={loading}>Save</Button>
        <Button variant="danger" className="w-full mb-8" onClick={() => { signOut(); navigate('/'); }}>Sign Out</Button>

        <div className="border-t border-bg-border pt-6">
          <p className="text-center text-sm text-text-muted mb-3">Enjoying Duitrip? Support development ☕</p>
          <KofiWidget />
        </div>
      </div>
    </div>
  );
}
