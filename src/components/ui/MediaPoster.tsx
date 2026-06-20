import Image from "next/image";
import { Film, Tv } from "lucide-react";
import { clsx } from "clsx";

interface MediaPosterProps {
  posterPath?: string | null;
  title: string;
  type: "movie" | "tv";
  sizes?: string;
  className?: string;
}

export default function MediaPoster({ posterPath, title, type, sizes = "(max-width: 768px) 33vw, 200px", className }: MediaPosterProps) {
  const Icon = type === "movie" ? Film : Tv;

  return (
    <div className={clsx("aspect-[2/3] relative overflow-hidden bg-gradient-to-br from-brand-primary/20 to-brand-secondary/10", className)}>
      {posterPath ? (
        <Image src={posterPath} alt={title} fill sizes={sizes} className="object-cover" />
      ) : (
        <div className="absolute inset-0 flex items-center justify-center">
          <Icon className={clsx("opacity-40", type === "movie" ? "text-brand-primary w-10 h-10" : "text-brand-secondary w-10 h-10")} />
        </div>
      )}
    </div>
  );
}
