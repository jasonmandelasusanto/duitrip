import type { TripMember } from '../../types';
import { Avatar } from '../ui/Avatar';
import { Badge } from '../ui/Badge';

interface MemberListProps {
  members: TripMember[];
  ownerId?: string;
  isOwner?: boolean;
  onPromoteGhost?: (ghostId: string) => void;
  onRemoveMember?: (memberId: string) => void;
  onEditGhost?: (ghostId: string, currentName: string, currentCurrency: string) => void;
}

export function MemberList({ members, ownerId, isOwner, onPromoteGhost, onRemoveMember, onEditGhost }: MemberListProps) {
  return (
    <div className="flex flex-col gap-2">
      {members.map((m) => {
        const uid = m.userId || m.ghostId || m.displayName;
        const isThisOwner = m.role === 'owner' || m.userId === ownerId;
        const removableId = m.userId || m.ghostId;

        return (
          <div key={uid} className="bg-bg-surface border border-bg-border rounded-xl p-3 flex items-center gap-3">
            <Avatar src={m.photoURL} name={m.displayName} isGhost={m.role === 'ghost'} />
            <div className="flex-1 min-w-0">
              <p className="text-sm font-medium text-text-primary truncate">{m.displayName}</p>
              <p className="text-xs text-text-muted">{m.email || 'Not on app'} · {m.homeCurrency}</p>
            </div>
            <div className="flex items-center gap-2">
              {isThisOwner && <Badge variant="teal">Owner</Badge>}
              {m.role === 'ghost' && <Badge variant="ghost">👻 Ghost</Badge>}
              {m.role === 'ghost' && isOwner && onEditGhost && m.ghostId && (
                <button
                  onClick={() => onEditGhost(m.ghostId!, m.displayName, m.homeCurrency)}
                  className="text-xs text-text-muted hover:text-teal transition-colors"
                  title="Edit buddy"
                >
                  <svg xmlns="http://www.w3.org/2000/svg" className="w-3.5 h-3.5" viewBox="0 0 20 20" fill="currentColor">
                    <path d="M13.586 3.586a2 2 0 112.828 2.828l-.793.793-2.828-2.828.793-.793zM11.379 5.793L3 14.172V17h2.828l8.38-8.379-2.83-2.828z" />
                  </svg>
                </button>
              )}
              {m.role === 'ghost' && isOwner && onPromoteGhost && m.ghostId && (
                <button
                  onClick={() => onPromoteGhost(m.ghostId!)}
                  className="text-xs text-teal hover:underline"
                >
                  Promote
                </button>
              )}
              {isOwner && !isThisOwner && removableId && onRemoveMember && (
                <button
                  onClick={() => onRemoveMember(removableId)}
                  className="text-xs text-danger hover:underline"
                >
                  Remove
                </button>
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
}
