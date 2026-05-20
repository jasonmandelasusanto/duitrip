import { useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../services/api';
import { useAppStore } from '../store/useAppStore';
import { Input } from '../components/ui/Input';
import { Button } from '../components/ui/Button';
import { Avatar } from '../components/ui/Avatar';
import { signOut } from '../services/auth';
import { uploadProfilePicture } from '../utils/imageUpload';
import { exportAllData, parseImportXLSX } from '../utils/dataExport';
import type { ImportRow } from '../utils/dataExport';

const POPULAR = ['USD', 'EUR', 'SGD', 'IDR', 'THB', 'MYR', 'JPY', 'AUD', 'GBP', 'HKD'];
const MAX_BYTES = 5 * 1024 * 1024;

export default function Profile() {
  const { user, setUser, trips } = useAppStore();
  const navigate = useNavigate();
  const [displayName, setDisplayName] = useState(user?.displayName || '');
  const [currency, setCurrency] = useState(user?.homeCurrency || 'USD');
  const [loading, setLoading] = useState(false);
  const [photoLoading, setPhotoLoading] = useState(false);
  const [photoError, setPhotoError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const importFileRef = useRef<HTMLInputElement>(null);
  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [deleteConfirmText, setDeleteConfirmText] = useState('');
  const [deleteLoading, setDeleteLoading] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);

  // Import state
  const [importRows, setImportRows] = useState<ImportRow[] | null>(null);
  const [importError, setImportError] = useState<string | null>(null);
  const [importing, setImporting] = useState(false);
  const [importDone, setImportDone] = useState<{ ok: number; failed: number } | null>(null);

  async function handleSave() {
    setLoading(true);
    try {
      await api.patch('/users/me', { displayName: displayName.trim() || undefined, homeCurrency: currency });
      setUser({ ...user!, displayName: displayName.trim() || user!.displayName, homeCurrency: currency });
      navigate('/dashboard');
    } finally { setLoading(false); }
  }

  async function handleDeleteAccount() {
    setDeleteError(null);
    setDeleteLoading(true);
    try {
      await api.delete('/users/me');
      setUser(null);
      navigate('/');
    } catch {
      setDeleteError('Failed to delete account. Please try again.');
    } finally {
      setDeleteLoading(false);
    }
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

  async function handleExport() {
    setExportError(null);
    setExporting(true);
    try {
      await exportAllData(trips, api);
    } catch (err) {
      setExportError(err instanceof Error ? err.message : 'Export failed');
    } finally {
      setExporting(false);
    }
  }

  async function handleImportFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    setImportError(null);
    setImportRows(null);
    setImportDone(null);
    try {
      const rows = await parseImportXLSX(file, trips);
      setImportRows(rows);
    } catch (err) {
      setImportError(err instanceof Error ? err.message : 'Failed to parse file');
    } finally {
      e.target.value = '';
    }
  }

  async function handleConfirmImport() {
    if (!importRows) return;
    const valid = importRows.filter((r) => r.valid);
    setImporting(true);
    let ok = 0;
    let failed = 0;
    for (const row of valid) {
      try {
        await api.post(`/trips/${row.tripId}/expenses`, {
          description: row.description,
          category: row.category,
          paidBy: row.paidById,
          originalAmount: row.originalAmount,
          originalCurrency: row.originalCurrency,
          expenseDate: row.date || undefined,
          splitMode: 'equal',
          splits: row.splitMemberIds && row.splitMemberIds.length > 0
            ? row.splitMemberIds.map((id) => ({ userId: id }))
            : [],
          notes: row.notes || null,
        });
        ok++;
      } catch {
        failed++;
      }
    }
    setImporting(false);
    setImportRows(null);
    setImportDone({ ok, failed });
  }

  const validCount = importRows?.filter((r) => r.valid).length ?? 0;
  const errorCount = importRows?.filter((r) => !r.valid).length ?? 0;

  return (
    <>
      <div className="min-h-screen bg-bg-base">
        <div className="max-w-2xl mx-auto px-4 pt-8 pb-10">
          <button onClick={() => navigate(-1)} className="text-text-secondary text-sm mb-6">← Back</button>
          <h1 className="text-2xl font-bold text-text-primary mb-6">Profile</h1>

          <div className="bg-bg-surface border border-bg-border rounded-2xl p-4 mb-6 flex items-center gap-4">
            <div className="relative flex-shrink-0">
              <Avatar src={user?.photoURL} name={displayName || user?.displayName || ''} size="lg" />
              <button
                onClick={() => fileInputRef.current?.click()}
                disabled={photoLoading}
                title="Change photo"
                className="absolute -bottom-1 -right-1 w-6 h-6 bg-teal rounded-full flex items-center justify-center hover:bg-teal-dark transition-colors disabled:opacity-60"
              >
                {photoLoading ? (
                  <div className="w-3 h-3 border-2 border-white/40 border-t-white rounded-full animate-spin" />
                ) : (
                  <svg xmlns="http://www.w3.org/2000/svg" width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3l-2.5-3z"/>
                    <circle cx="12" cy="13" r="3"/>
                  </svg>
                )}
              </button>
            </div>
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

          <Button className="w-full mb-8" onClick={handleSave} disabled={loading}>Save</Button>

          {/* Import & Export */}
          <div className="bg-bg-surface border border-bg-border rounded-2xl p-4 mb-6">
            <h2 className="font-semibold text-text-primary mb-1">Import & Export</h2>
            <p className="text-xs text-text-muted mb-4">
              Export all trips to a multi-sheet .xlsx file, or import expenses from a previously exported file.
            </p>

            <div className="flex flex-col gap-2">
              <Button onClick={handleExport} disabled={exporting || trips.length === 0}>
                {exporting ? 'Exporting…' : '↓ Export All Data (.xlsx)'}
              </Button>
              {trips.length === 0 && (
                <p className="text-xs text-text-muted">No trips to export.</p>
              )}
              {exportError && <p className="text-xs text-danger">{exportError}</p>}

              <Button variant="ghost" onClick={() => { setImportDone(null); importFileRef.current?.click(); }}>
                ↑ Import Expenses (.xlsx)
              </Button>
              <input ref={importFileRef} type="file" accept=".xlsx" className="hidden" onChange={handleImportFileChange} />
              {importError && <p className="text-xs text-danger">{importError}</p>}
              {importDone && (
                <p className="text-xs text-success">
                  Imported {importDone.ok} expense{importDone.ok !== 1 ? 's' : ''}{importDone.failed > 0 ? `, ${importDone.failed} failed` : ''}.
                </p>
              )}
            </div>

            <p className="text-xs text-text-muted mt-3">
              Import note: expenses are split equally among all trip members. Trip names must match exactly.
            </p>
          </div>

          <Button variant="danger" className="w-full mb-3" onClick={() => { signOut(); navigate('/'); }}>Sign Out</Button>
          <Button variant="ghost" className="w-full text-danger border border-danger/30 hover:bg-danger/10" onClick={() => { setShowDeleteModal(true); setDeleteConfirmText(''); setDeleteError(null); }}>
            Delete Account
          </Button>
        </div>
      </div>

      {/* Import preview modal */}
      {importRows && (
        <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center p-4 bg-black/60">
          <div className="bg-bg-surface rounded-2xl w-full max-w-lg border border-bg-border shadow-2xl flex flex-col max-h-[80vh]">
            <div className="p-5 border-b border-bg-border shrink-0">
              <h2 className="text-base font-semibold text-text-primary">Import Preview</h2>
              <p className="text-sm text-text-secondary mt-1">
                <span className="text-success font-medium">{validCount} ready</span>
                {errorCount > 0 && <span className="text-danger font-medium ml-2">{errorCount} skipped</span>}
              </p>
            </div>

            <div className="overflow-y-auto flex-1 px-5 py-3 flex flex-col gap-2">
              {importRows.map((row, i) => (
                <div key={i} className={`rounded-xl px-3 py-2.5 border text-sm ${row.valid ? 'border-bg-border bg-bg-elevated' : 'border-danger/30 bg-danger/5'}`}>
                  <div className="flex items-start gap-2">
                    <span className={`shrink-0 text-xs font-semibold mt-0.5 ${row.valid ? 'text-success' : 'text-danger'}`}>
                      {row.valid ? '✓' : '✗'}
                    </span>
                    <div className="min-w-0">
                      <p className="font-medium text-text-primary truncate">{row.description || '(no description)'}</p>
                      <p className="text-xs text-text-muted">
                        {row.tripName} · {row.paidByName} · {row.originalAmount} {row.originalCurrency}
                        {row.date ? ` · ${row.date}` : ''}
                      </p>
                      {row.errors.length > 0 && (
                        <p className="text-xs text-danger mt-0.5">{row.errors.join(', ')}</p>
                      )}
                    </div>
                  </div>
                </div>
              ))}
            </div>

            <div className="p-5 border-t border-bg-border shrink-0 flex gap-3">
              <Button
                className="flex-1"
                onClick={handleConfirmImport}
                disabled={validCount === 0 || importing}
              >
                {importing ? 'Importing…' : `Import ${validCount} expense${validCount !== 1 ? 's' : ''}`}
              </Button>
              <Button variant="ghost" onClick={() => setImportRows(null)}>Cancel</Button>
            </div>
          </div>
        </div>
      )}

      {showDeleteModal && (
        <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center p-4 bg-black/60">
          <div className="bg-bg-surface rounded-xl w-full max-w-md border border-bg-border shadow-2xl">
            <div className="p-5 border-b border-bg-border">
              <h2 className="text-base font-semibold text-text-primary">Delete Account</h2>
              <p className="text-sm text-text-secondary mt-1">
                This permanently deletes your account and all your data. This cannot be undone.
              </p>
            </div>
            <div className="p-5 space-y-4">
              <div>
                <label className="block text-xs text-text-secondary mb-1.5">
                  Type <span className="font-semibold text-text-primary">delete</span> to confirm
                </label>
                <input
                  type="text"
                  value={deleteConfirmText}
                  onChange={(e) => setDeleteConfirmText(e.target.value)}
                  placeholder="delete"
                  className="w-full bg-bg-base border border-bg-border rounded-lg px-3 py-2 text-sm text-text-primary placeholder:text-text-muted focus:outline-none focus:border-danger"
                />
              </div>
              {deleteError && (
                <p className="text-sm text-danger bg-danger/10 rounded-lg px-3 py-2">{deleteError}</p>
              )}
              <div className="flex gap-3">
                <button
                  onClick={() => setShowDeleteModal(false)}
                  className="flex-1 py-2.5 rounded-lg border border-bg-border text-sm text-text-secondary hover:text-text-primary transition-colors"
                >
                  Cancel
                </button>
                <button
                  onClick={handleDeleteAccount}
                  disabled={deleteLoading || deleteConfirmText !== 'delete'}
                  className="flex-1 py-2.5 rounded-lg bg-danger text-white text-sm font-semibold hover:bg-danger/90 disabled:opacity-50 transition-colors"
                >
                  {deleteLoading ? 'Deleting…' : 'Delete Forever'}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
