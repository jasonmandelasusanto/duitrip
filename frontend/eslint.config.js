import js from '@eslint/js';
import tseslint from 'typescript-eslint';
import reactHooks from 'eslint-plugin-react-hooks';

export default tseslint.config(
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    plugins: { 'react-hooks': reactHooks },
    rules: {
      // Catches useState/useEffect called conditionally or after an early return — this
      // is what caused the React error #310 crash on the Landing page.
      'react-hooks/rules-of-hooks': 'error',
      // Catches missing effect dependencies that can cause stale-closure bugs.
      'react-hooks/exhaustive-deps': 'warn',
      // Catch unused imports/variables (allow _ prefix for intentional ignores).
      '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_', varsIgnorePattern: '^_' }],
      // Allow `any` with a warning — avoids noise on third-party types.
      '@typescript-eslint/no-explicit-any': 'warn',
    },
  },
);
