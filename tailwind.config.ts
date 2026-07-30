import type { Config } from "tailwindcss";

const config: Config = {
  content: [
    "./src/pages/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/components/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/app/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      colors: {
        brand: {
          bg: "#060910",
          primary: "#6C63FF",
          secondary: "#00D4FF",
          accent: "#FF4757",
          text: "#FFFFFF",
          muted: "#9BA3B8",
          card: "rgba(15, 20, 36, 0.6)",
          border: "rgba(124, 118, 255, 0.22)",
          hr: "#CE2C1A",
        },
        // Deep Space & Neon Accent — the same token set the Android client's
        // PaletteDeepSpace implements, so a screenshot of the app and a screen
        // of the site read as one product. Added alongside `brand` rather than
        // replacing it: `brand` is referenced across the existing marketing
        // pages, and swapping those hexes underneath them would restyle every
        // one of them sight-unseen.
        space: {
          // Surface ladder: 0 = page, 1 = card, 2 = hover / dialog.
          0: "#0B0E14",
          1: "#121824",
          2: "#1A2332",
          border: "#2A364F",
          // Accents.
          primary: "#00F0FF",
          secondary: "#7B2CBF",
          success: "#10B981",
          alert: "#EF4444",
          // Text ladder: primary / secondary / tertiary.
          text: "#F8FAFC",
          "text-dim": "#94A3B8",
          "text-faint": "#64748B",
        },
      },
      fontFamily: {
        sans: ["var(--font-inter)", "Inter", "system-ui", "sans-serif"],
      },
      backgroundImage: {
        "gradient-radial": "radial-gradient(var(--tw-gradient-stops))",
        "hero-gradient":
          "linear-gradient(135deg, #080B16 0%, #0D1F3C 50%, #080B16 100%)",
      },
      // Glassmorphism + elevation tokens, matching the client's GlassCard.
      backdropBlur: {
        glass: "12px",
        "glass-lg": "20px",
      },
      boxShadow: {
        // Soft ambient elevation for raised panels.
        elevated: "0 10px 30px -10px rgba(0, 0, 0, 0.8)",
        // Focus/selection glow — the web echo of the client's D-pad ring.
        "focus-glow": "0 0 0 2px #00F0FF, 0 0 12px 0 rgba(0, 240, 255, 0.35)",
      },
      animation: {
        // Telemetry indicators: a slow breathing dot reads as "live" without
        // pulling the eye the way a blink does.
        "micro-pulse": "microPulse 2s ease-in-out infinite",
        "pulse-glow": "pulseGlow 3s ease-in-out infinite",
        float: "float 6s ease-in-out infinite",
        "typing-cursor": "blink 1s step-end infinite",
        "fade-in": "fadeIn 0.5s ease-in-out",
        "slide-up": "slideUp 0.5s ease-out",
        shimmer: "shimmer 2s linear infinite",
        "marquee": "marquee 70s linear infinite",
        "marquee-reverse": "marquee 70s linear infinite reverse",
      },
      keyframes: {
        microPulse: {
          "0%, 100%": { opacity: "1", boxShadow: "0 0 0 0 rgba(16, 185, 129, 0.7)" },
          "50%": { opacity: "0.75", boxShadow: "0 0 0 6px rgba(16, 185, 129, 0)" },
        },
        pulseGlow: {
          "0%, 100%": {
            boxShadow: "0 0 20px rgba(47, 111, 255, 0.5)",
          },
          "50%": {
            boxShadow: "0 0 40px rgba(47, 111, 255, 0.8), 0 0 80px rgba(31, 216, 242, 0.3)",
          },
        },
        float: {
          "0%, 100%": { transform: "translateY(0px)" },
          "50%": { transform: "translateY(-20px)" },
        },
        blink: {
          "0%, 100%": { opacity: "1" },
          "50%": { opacity: "0" },
        },
        fadeIn: {
          from: { opacity: "0" },
          to: { opacity: "1" },
        },
        slideUp: {
          from: { opacity: "0", transform: "translateY(20px)" },
          to: { opacity: "1", transform: "translateY(0)" },
        },
        shimmer: {
          "0%": { backgroundPosition: "-200% 0" },
          "100%": { backgroundPosition: "200% 0" },
        },
        marquee: {
          "0%": { transform: "translateX(0)" },
          "100%": { transform: "translateX(-50%)" },
        },
      },
    },
  },
  plugins: [],
};
export default config;
