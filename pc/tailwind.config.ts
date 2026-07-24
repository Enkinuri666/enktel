import type { Config } from 'tailwindcss';

/**
 * EnkTel IPTV desktop — Tailwind config wired to the same brand palette as the
 * Android app (default "EnkTel Blue" theme). All colour tokens are exposed as
 * both Tailwind classes AND CSS variables so downstream libraries (Radix,
 * shadcn-style components, motion) can consume them.
 */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        // Brand
        bg: '#0A0E17',
        surface: '#121826',
        surfaceHi: '#1B2333',
        text: '#EAF0FA',
        textDim: '#93A0B8',
        border: '#2A3550',
        // Accents
        brand: {
          DEFAULT: '#3B9DFF',
          deep: '#1B6AE5',
          purple: '#8B5CF6',
        },
        live: '#EF4444',
        ok: '#34D399',
      },
      fontFamily: {
        sans: [
          'Inter var', 'Inter', 'system-ui', 'Segoe UI', 'Roboto',
          'Helvetica Neue', 'Arial', 'sans-serif',
        ],
      },
      boxShadow: {
        glass: '0 8px 32px 0 rgba(0, 0, 0, 0.4)',
        glow: '0 0 24px rgba(59, 157, 255, 0.35)',
      },
      backgroundImage: {
        'brand-gradient':
          'linear-gradient(135deg, #3B9DFF 0%, #1B6AE5 40%, #8B5CF6 100%)',
        'hero-fade':
          'linear-gradient(180deg, rgba(10,14,23,0) 0%, rgba(10,14,23,1) 90%)',
      },
      keyframes: {
        // Netflix-style pulse for LIVE dots
        livePulse: {
          '0%,100%': { opacity: '1', transform: 'scale(1)' },
          '50%': { opacity: '0.55', transform: 'scale(1.15)' },
        },
        // Signal-ring expanding out from the wordmark, for the startup splash
        splashRing: {
          '0%': { transform: 'scale(0)', opacity: '0.9' },
          '100%': { transform: 'scale(2.2)', opacity: '0' },
        },
        shimmer: {
          '0%': { transform: 'translateX(-100%)' },
          '100%': { transform: 'translateX(100%)' },
        },
      },
      animation: {
        livePulse: 'livePulse 1.6s ease-in-out infinite',
        splashRing: 'splashRing 2.4s ease-out infinite',
        shimmer: 'shimmer 2s linear infinite',
      },
    },
  },
} satisfies Config;
