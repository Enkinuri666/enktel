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
        "rounded-xl border border-brand-border",
        glass
          ? "bg-white/5 backdrop-blur-md"
          : "bg-brand-card",
        hover &&
          "hover:border-brand-primary/50 hover:shadow-lg hover:shadow-brand-primary/10 transition-all duration-300 cursor-pointer",
        onClick && "cursor-pointer",
        className
      )}
    >
      {children}
    </div>
  );
}
