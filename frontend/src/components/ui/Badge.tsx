import { type ReactNode } from 'react';

interface BadgeProps {
  children: ReactNode;
  variant?: 'teal' | 'amber' | 'success' | 'danger' | 'ghost';
}

export function Badge({ children, variant = 'teal' }: BadgeProps) {
  const variants = {
    teal: 'bg-teal/20 text-teal-light',
    amber: 'bg-amber/20 text-amber-light',
    success: 'bg-success/20 text-success',
    danger: 'bg-danger/20 text-danger',
    ghost: 'bg-bg-border text-text-muted',
  };
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${variants[variant]}`}>
      {children}
    </span>
  );
}
