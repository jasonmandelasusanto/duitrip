import type { TripMember } from '../../types';
import { Avatar } from '../ui/Avatar';
import { Badge } from '../ui/Badge';

interface MemberListProps {
  members: TripMember[];
  onPromoteGhost?: (ghostId: string) => void;
  isOwner?: boolean;
}

export function MemberList({ members, onPromoteGhost, isOwner }: MemberListProps) {
  return (
    <div className="flex flex-col gap-2">
      {members.map((m) => {
        const uid = m.userId || m.ghostId || m.displayName;
        return (
          <div key={uid} className="bg-bg-surface border border-bg-border rounded-xl p-3 flex items-center gap-3">
            <Avatar src={m.photoURL} name={m.displayName} isGhost={m.role === 'ghost'} />
            <div className="flex-1 min-w-0">
              <p className="text-sm font-medium text-text-primary truncate">{m.displayName}</p>
              <p className="text-xs text-text-muted">{m.email || 'Not on app'} · {m.homeCurrency}</p>
            </div>
            <div className="flex items-center gap-2">
              {m.role === 'owner' && <Badge variant="teal">Owner</Badge>}
              {m.role === 'ghost' && <Badge variant="ghost">👻 Ghost</Badge>}
              {m.role === 'ghost' && isOwner && onPromoteGhost && m.ghostId && (
                <button
                  onClick={() => onPromoteGhost(m.ghostId!)}
                  className="text-xs text-teal hover:text-teal-light"
                >
                  Promote
                </button>
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
}
