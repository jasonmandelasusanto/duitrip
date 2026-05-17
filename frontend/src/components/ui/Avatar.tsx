interface AvatarProps {
  src?: string | null;
  name: string;
  isGhost?: boolean;
  size?: 'sm' | 'md' | 'lg';
}

export function Avatar({ src, name, isGhost = false, size = 'md' }: AvatarProps) {
  const sizes = { sm: 'w-7 h-7 text-xs', md: 'w-9 h-9 text-sm', lg: 'w-12 h-12 text-base' };
  const initials = name.split(' ').map((n) => n[0]).join('').slice(0, 2).toUpperCase();

  if (src) {
    return (
      <div className={`${sizes[size]} rounded-full overflow-hidden ring-2 ring-bg-border relative`}>
        <img src={src} alt={name} className="w-full h-full object-cover" />
        {isGhost && <span className="absolute -bottom-0.5 -right-0.5 text-xs">👻</span>}
      </div>
    );
  }

  return (
    <div className={`${sizes[size]} rounded-full bg-bg-border flex items-center justify-center ring-2 ring-bg-base font-semibold text-text-secondary relative`}>
      {initials}
      {isGhost && <span className="absolute -bottom-0.5 -right-0.5 text-xs">👻</span>}
    </div>
  );
}
