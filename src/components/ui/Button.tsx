import React from "react";
import { clsx } from "clsx";

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: "primary" | "secondary" | "outline" | "ghost" | "danger" | "glass" | "glow";
  size?: "sm" | "md" | "lg";
  fullWidth?: boolean;
  loading?: boolean;
}

export default function Button({
  children,
  variant = "primary",
  size = "md",
  fullWidth = false,
  loading = false,
  className,
  disabled,
  ...props
}: ButtonProps) {
  const base =
    "inline-flex items-center justify-center font-semibold rounded-lg transition-all duration-200 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-offset-brand-bg disabled:opacity-50 disabled:cursor-not-allowed";

  const variants = {
    primary:
      "bg-brand-primary text-white hover:bg-purple-600 focus:ring-brand-primary shadow-lg shadow-brand-primary/25 hover:shadow-brand-primary/40",
    secondary:
      "bg-brand-secondary text-brand-bg hover:bg-cyan-400 focus:ring-brand-secondary shadow-lg shadow-brand-secondary/25",
    outline:
      "border border-brand-primary text-brand-primary hover:bg-brand-primary hover:text-white focus:ring-brand-primary",
    ghost:
      "text-brand-muted hover:text-white hover:bg-white/10 focus:ring-white/20",
    danger:
      "bg-brand-accent text-white hover:bg-red-600 focus:ring-brand-accent shadow-lg shadow-brand-accent/25",
    glass:
      "cyber-panel text-white hover:border-brand-secondary/40 focus:ring-brand-secondary/40",
    glow:
      "btn-glow bg-gradient-to-r from-brand-primary to-brand-secondary text-white focus:ring-brand-primary",
  };

  const sizes = {
    sm: "px-3 py-1.5 text-sm",
    md: "px-5 py-2.5 text-base",
    lg: "px-8 py-3.5 text-lg",
  };

  return (
    <button
      className={clsx(
        base,
        variants[variant],
        sizes[size],
        fullWidth && "w-full",
        className
      )}
      disabled={disabled || loading}
      {...props}
    >
      {loading && (
        <svg
          className="animate-spin -ml-1 mr-2 h-4 w-4"
          fill="none"
          viewBox="0 0 24 24"
        >
          <circle
            className="opacity-25"
            cx="12"
            cy="12"
            r="10"
            stroke="currentColor"
            strokeWidth="4"
          />
          <path
            className="opacity-75"
            fill="currentColor"
            d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"
          />
        </svg>
      )}
      {children}
    </button>
  );
}
