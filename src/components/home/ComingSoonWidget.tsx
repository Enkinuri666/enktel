"use client";
import { useState, useEffect } from "react";
import useSWR from "swr";
import Link from "next/link";
import Image from "next/image";
import { Clock, ChevronRight, Film } from "lucide-react";
import Spinner from "@/components/ui/Spinner";
import { Movie } from "@/types";

const fetcher = (url: string) => fetch(url).then((r) => r.json());

function useCountdown(targetDate: string) {
  const [timeLeft, setTimeLeft] = useState({ days: 0, hours: 0, minutes: 0, seconds: 0 });

  useEffect(() => {
    function calculate() {
      const diff = new Date(targetDate).getTime() - Date.now();
      if (diff <= 0) {
        setTimeLeft({ days: 0, hours: 0, minutes: 0, seconds: 0 });
        return;
      }
      setTimeLeft({
        days: Math.floor(diff / 86400000),
        hours: Math.floor((diff % 86400000) / 3600000),
        minutes: Math.floor((diff % 3600000) / 60000),
        seconds: Math.floor((diff % 60000) / 1000),
      });
    }
    calculate();
    const id = setInterval(calculate, 1000);
    return () => clearInterval(id);
  }, [targetDate]);

  return timeLeft;
}

function CountdownCard({ movie }: { movie: Movie }) {
  const { days, hours, minutes, seconds } = useCountdown(movie.releaseDate);
  const releaseDate = new Date(movie.releaseDate).toLocaleDateString("en-GB", {
    day: "numeric",
    month: "long",
    year: "numeric",
  });

  return (
    <div className="cyber-panel rounded-xl p-5 hover:border-brand-primary/40 transition-all duration-300">
      <div className="flex items-start gap-4">
        <div className="w-16 h-16 rounded-xl shrink-0 relative overflow-hidden bg-gradient-to-br from-brand-primary/30 to-brand-secondary/20">
          {movie.posterPath ? (
            <Image src={movie.posterPath} alt={movie.title} fill sizes="64px" className="object-cover" />
          ) : (
            <div className="absolute inset-0 flex items-center justify-center">
              <Film className="w-8 h-8 text-brand-primary/60" />
            </div>
          )}
        </div>
        <div className="flex-1 min-w-0">
          <h3 className="text-white font-semibold text-sm mb-1 line-clamp-1">{movie.title}</h3>
          <p className="text-brand-muted text-xs mb-1">{releaseDate}</p>
          <div className="flex flex-wrap gap-1 mb-3">
            {movie.genres.slice(0, 2).map((g) => (
              <span key={g} className="text-xs bg-white/5 text-brand-muted px-2 py-0.5 rounded-full">{g}</span>
            ))}
          </div>
          {/* Countdown */}
          <div className="flex items-center gap-2">
            <Clock className="w-3 h-3 text-brand-accent shrink-0" />
            <div className="flex items-center gap-1 text-xs">
              {days > 0 && <span className="text-brand-secondary font-bold">{days}d</span>}
              <span className="text-brand-primary font-bold">{String(hours).padStart(2, "0")}h</span>
              <span className="text-brand-muted">:</span>
              <span className="text-brand-primary font-bold">{String(minutes).padStart(2, "0")}m</span>
              <span className="text-brand-muted">:</span>
              <span className="text-brand-primary font-bold">{String(seconds).padStart(2, "0")}s</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

export default function ComingSoonWidget() {
  const { data, isLoading } = useSWR<{ movies: Movie[] }>("/api/coming-soon", fetcher);
  const movies = data?.movies?.slice(0, 4) || [];

  return (
    <section className="py-16 px-4 sm:px-6 lg:px-8">
      <div className="max-w-7xl mx-auto">
        <div className="flex items-center justify-between mb-8">
          <h2 className="text-2xl sm:text-3xl font-bold text-white">
            Coming{" "}
            <span className="bg-gradient-to-r from-brand-secondary to-brand-primary bg-clip-text text-transparent">
              Soon
            </span>
          </h2>
          <Link
            href="/coming-soon"
            className="flex items-center gap-1 text-brand-primary hover:text-brand-secondary transition-colors text-sm font-medium"
          >
            See all <ChevronRight className="w-4 h-4" />
          </Link>
        </div>

        {isLoading ? (
          <Spinner className="py-12" />
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {movies.map((movie) => (
              <CountdownCard key={movie.id} movie={movie} />
            ))}
          </div>
        )}
      </div>
    </section>
  );
}
