import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useTrip } from '../hooks/useTrip';
import api from '../services/api';
import { TripHeader } from '../components/trip/TripHeader';
import { MemberList } from '../components/trip/MemberList';
import { Modal } from '../components/ui/Modal';
import { ConfirmDialog } from '../components/ui/ConfirmDialog';
import { Input } from '../components/ui/Input';
import { Button } from '../components/ui/Button';
import { useAppStore } from '../store/useAppStore';

const TABS = [
  { label: 'Expenses', path: (id: string) => `/trips/${id}`, icon: '💸' },
  { label: 'Analytics', path: (id: string) => `/trips/${id}/analytics`, icon: '📊' },
  { label: 'Members', path: (id: string) => `/trips/${id}/members`, icon: '👥' },
  { label: 'Settle Up', path: (id: string) => `/trips/${id}/settlement`, icon: '💰' },
];

function desktopTabClass(label: string) {
  if (label === 'Members') return 'bg-teal text-white';
  if (label === 'Settle Up') return 'text-amber hover:bg-amber/10';
  return 'text-text-secondary hover:bg-bg-elevated';
}

export default function Members() {
  const { tripId } = useParams<{ tripId: string }>();
  const { trip } = useTrip(tripId);
  const { user } = useAppStore();
  const navigate = useNavigate();
  const [inviteModal, setInviteModal] = useState(false);
  const [ghostModal, setGhostModal] = useState(false);
  const [email, setEmail] = useState('');
  const [ghostName, setGhostName] = useState('');
  const [ghostCurrency, setGhostCurrency] = useState('USD');
  const [loading, setLoading] = useState(false);
  const [promoteGhostId, setPromoteGhostId] = useState<string | null>(null);
  const [promoteEmail, setPromoteEmail] = useState('');
  const [removeTargetId, setRemoveTargetId] = useState<string | null>(null);
  const [editGhostId, setEditGhostId] = useState<string | null>(null);
  const [editGhostName, setEditGhostName] = useState('');
  const [editGhostCurrency, setEditGhostCurrency] = useState('');

  if (!trip) return <div className="min-h-screen bg-bg-base flex items-center justify-center text-text-muted">Loading…</div>;

  const isOwner = trip.createdBy === user?.uid;

  async function sendInvite() {
    if (!tripId || !email) return;
    setLoading(true);
    try {
      await api.post(`/trips/${tripId}/invites`, { email });
      setEmail('');
      setInviteModal(false);
    } finally { setLoading(false); }
  }

  async function addGhost() {
    if (!tripId || !ghostName) return;
    setLoading(true);
    try {
      await api.post(`/trips/${tripId}/members/ghost`, { displayName: ghostName, homeCurrency: ghostCurrency });
      setGhostName('');
      setGhostModal(false);
    } finally { setLoading(false); }
  }

  async function confirmRemoveMember() {
    if (!tripId || !removeTargetId) return;
    const targetId = removeTargetId;
    setRemoveTargetId(null);
    await api.delete(`/trips/${tripId}/members/${targetId}`);
  }

  async function saveEditGhost() {
    if (!tripId || !editGhostId || !editGhostName.trim()) return;
    setLoading(true);
    try {
      await api.patch(`/trips/${tripId}/members/ghost/${editGhostId}`, {
        displayName: editGhostName.trim(),
        homeCurrency: editGhostCurrency.toUpperCase(),
      });
      setEditGhostId(null);
    } finally { setLoading(false); }
  }

  async function promoteGhost() {
    if (!tripId || !promoteGhostId || !promoteEmail) return;
    setLoading(true);
    try {
      await api.post(`/trips/${tripId}/members/ghost/${promoteGhostId}/promote`, { email: promoteEmail });
      setPromoteGhostId(null);
      setPromoteEmail('');
    } finally { setLoading(false); }
  }

  const MembersContent = () => (
    <>
      <MemberList
        members={trip.members}
        ownerId={trip.createdBy}
        isOwner={isOwner}
        onEditGhost={isOwner ? (id, name, cur) => { setEditGhostId(id); setEditGhostName(name); setEditGhostCurrency(cur); } : undefined}
        onPromoteGhost={(ghostId) => setPromoteGhostId(ghostId)}
        onRemoveMember={isOwner ? (id) => setRemoveTargetId(id) : undefined}
      />
      <div className="flex gap-3 mt-4">
        <Button variant="ghost" className="flex-1" onClick={() => setGhostModal(true)}>
          + Add buddy (no app)
        </Button>
        <Button className="flex-1" onClick={() => setInviteModal(true)}>
          + Invite member
        </Button>
      </div>
      {(trip.invites ?? []).filter((i) => i.status === 'pending').length > 0 && (
        <div className="mt-4">
          <p className="text-xs text-text-muted mb-2 font-medium">Pending Invites</p>
          {(trip.invites ?? []).filter((i) => i.status === 'pending').map((inv, idx) => (
            <p key={idx} className="text-sm text-text-secondary bg-bg-surface border border-bg-border rounded-xl px-3 py-2 mb-2">
              ✉️ {inv.email}
            </p>
          ))}
        </div>
      )}
    </>
  );

  return (
    <div className="min-h-screen bg-bg-base">
      {/* Mobile */}
      <div className="lg:hidden max-w-2xl mx-auto px-4 pt-6 pb-24">
        <button onClick={() => navigate(`/trips/${tripId}`)} className="text-text-secondary text-sm mb-4">← Back</button>
        <TripHeader trip={trip} />
        <div className="flex bg-bg-surface border border-bg-border rounded-xl p-1 gap-1 mb-4">
          {TABS.map((tab) => (
            <Link key={tab.label} to={tab.path(tripId!)}
              className={`flex-1 text-center py-2 text-xs font-medium rounded-lg transition-colors ${
                tab.label === 'Members' ? 'bg-teal text-white' : tab.label === 'Settle Up' ? 'text-amber hover:bg-bg-elevated' : 'text-text-secondary hover:bg-bg-elevated'
              }`}
            >{tab.label}</Link>
          ))}
        </div>
        <MembersContent />
      </div>

      {/* Desktop */}
      <div className="hidden lg:flex min-h-screen">
        <div className="w-80 shrink-0 border-r border-bg-border bg-bg-surface flex flex-col sticky top-0 h-screen overflow-y-auto">
          <div className="p-5">
            <button onClick={() => navigate('/dashboard')} className="text-text-secondary text-sm mb-4 hover:text-text-primary block">← All Trips</button>
            <TripHeader trip={trip} />
          </div>
          <nav className="px-3 pb-3 flex flex-col gap-1">
            {TABS.map((tab) => (
              <Link key={tab.label} to={tab.path(tripId!)}
                className={`flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium transition-colors ${desktopTabClass(tab.label)}`}
              >
                <span>{tab.icon}</span>
                {tab.label}
              </Link>
            ))}
          </nav>
        </div>
        <div className="flex-1 min-w-0 px-6 pt-6 pb-10 overflow-y-auto">
          <div className="max-w-2xl">
            <MembersContent />
          </div>
        </div>
      </div>

      <Modal open={!!editGhostId} onClose={() => setEditGhostId(null)} title="Edit Buddy">
        <Input label="Name" value={editGhostName} onChange={(e) => setEditGhostName(e.target.value)} />
        <div className="mt-3">
          <Input label="Home currency" value={editGhostCurrency} onChange={(e) => setEditGhostCurrency(e.target.value.toUpperCase())} maxLength={3} />
        </div>
        <Button className="w-full mt-4" onClick={saveEditGhost} disabled={!editGhostName.trim() || loading}>Save</Button>
      </Modal>

      <Modal open={inviteModal} onClose={() => setInviteModal(false)} title="Invite Member">
        <Input label="Email address" type="email" placeholder="friend@example.com" value={email} onChange={(e) => setEmail(e.target.value)} />
        <Button className="w-full mt-4" onClick={sendInvite} disabled={!email || loading}>Send Invite</Button>
      </Modal>

      <Modal open={ghostModal} onClose={() => setGhostModal(false)} title="Add Trip Buddy">
        <p className="text-sm text-text-muted mb-4">For friends who don't want to use the app.</p>
        <Input label="Name" placeholder="Budi" value={ghostName} onChange={(e) => setGhostName(e.target.value)} />
        <div className="mt-3">
          <Input label="Home currency" value={ghostCurrency} onChange={(e) => setGhostCurrency(e.target.value.toUpperCase())} maxLength={3} />
        </div>
        <Button className="w-full mt-4" onClick={addGhost} disabled={!ghostName || loading}>Add Buddy</Button>
      </Modal>

      <Modal open={!!promoteGhostId} onClose={() => setPromoteGhostId(null)} title="Promote to Real Member">
        <p className="text-sm text-text-muted mb-4">Send an invite email to promote this ghost member to a real account.</p>
        <Input label="Their email" type="email" value={promoteEmail} onChange={(e) => setPromoteEmail(e.target.value)} />
        <Button className="w-full mt-4" onClick={promoteGhost} disabled={!promoteEmail || loading}>Send Invite</Button>
      </Modal>

      <ConfirmDialog
        open={!!removeTargetId}
        title="Remove member?"
        message={`Remove ${trip.members.find((m) => (m.userId || m.ghostId) === removeTargetId)?.displayName ?? 'this member'} from the trip?`}
        confirmLabel="Remove"
        variant="danger"
        onConfirm={confirmRemoveMember}
        onCancel={() => setRemoveTargetId(null)}
      />
    </div>
  );
}
