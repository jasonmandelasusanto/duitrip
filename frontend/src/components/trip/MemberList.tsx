import type { TripMember } from '../../types';
import { Avatar } from '../ui/Avatar';
import { Badge } from '../ui/Badge';

interface MemberListProps {
  members: TripMember[];
  ownerId?: string;
  isOwner?: boolean;
  onPromoteGhost?: (ghostId: string) => void;
  onRemoveMember?: (memberId: string) => void;
}

export function MemberList({ members, ownerId, isOwner, onPromoteGhost, onRemoveMember }: MemberListProps) {
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
