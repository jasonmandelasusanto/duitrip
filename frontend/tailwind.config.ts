import type { Config } from 'tailwindcss';

export default {
  content: ['./src/**/*.{ts,tsx}', './index.html'],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        bg: {
          base: '#080C14',
          surface: '#111827',
          elevated: '#1A2235',
          border: '#263348',
        },
        teal: {
          light: '#82D8F3',
          DEFAULT: '#4DC3EA',
          dark: '#229EC8',
        },
        amber: {
          light: '#FCD34D',
          DEFAULT: '#F59E0B',
          dark: '#D97706',
        },
        success: '#10B981',
        danger: '#EF4444',
        warning: '#F97316',
        text: {
          primary: '#F1F5F9',
          secondary: '#94A3B8',
          muted: '#475569',
          disabled: '#334155',
        },
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
      },
    },
  },
  plugins: [],
} satisfies Config;
