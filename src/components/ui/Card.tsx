import React from "react";
import { clsx } from "clsx";

interface CardProps {
  children: React.ReactNode;
  className?: string;
  hover?: boolean;
  glass?: boolean;
  onClick?: () => void;
}

export default function Card({ children, className, hover = false, glass = false, onClick }: CardProps) {
  return (
    <div
      onClick={onClick}
      className={clsx(
        "rounded-2xl border border-brand-border",
        glass
          ? "glass-card"
          : "bg-brand-card",
        hover && (glass ? "glass-card-hover" : "card-hover hover:border-brand-primary/50"),
        onClick && "cursor-pointer",
        className
      )}
    >
      {children}
    </div>
  );
}
