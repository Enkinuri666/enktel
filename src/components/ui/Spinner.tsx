import React from "react";
import { clsx } from "clsx";

interface SpinnerProps {
  size?: "sm" | "md" | "lg";
  className?: string;
}

export default function Spinner({ size = "md", className }: SpinnerProps) {
  const sizes = { sm: "h-4 w-4", md: "h-8 w-8", lg: "h-12 w-12" };

  return (
    <div className={clsx("flex items-center justify-center", className)}>
      <div
        className={clsx(
          "animate-spin rounded-full border-2 border-brand-border border-t-brand-primary",
          sizes[size]
        )}
      />
    </div>
  );
}
