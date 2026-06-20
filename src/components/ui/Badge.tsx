import React from "react";
import { clsx } from "clsx";

interface BadgeProps {
  children: React.ReactNode;
  variant?: "primary" | "secondary" | "accent" | "success" | "warning" | "gold" | "default";
  size?: "sm" | "md";
  className?: string;
}

export default function Badge({ children, variant = "default", size = "sm", className }: BadgeProps) {
  const variants = {
    primary: "bg-brand-primary/20 text-brand-primary border-brand-primary/30",
    secondary: "bg-brand-secondary/20 text-brand-secondary border-brand-secondary/30",
    accent: "bg-brand-accent/20 text-brand-accent border-brand-accent/30",
    success: "bg-green-500/20 text-green-400 border-green-500/30",
    warning: "bg-yellow-500/20 text-yellow-400 border-yellow-500/30",
    gold: "bg-gradient-to-r from-yellow-400 to-yellow-500 text-black border-transparent font-black",
    default: "bg-white/10 text-brand-muted border-white/20",
  };

  const sizes = {
    sm: "px-2 py-0.5 text-xs",
    md: "px-3 py-1 text-sm",
  };

  return (
    <span
      className={clsx(
        "inline-flex items-center gap-1.5 font-medium rounded-full border",
        variants[variant],
        sizes[size],
        className
      )}
    >
      {children}
    </span>
  );
}
