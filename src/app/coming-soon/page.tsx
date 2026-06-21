"use client";
import { useState, useEffect } from "react";
import useSWR from "swr";
import Image from "next/image";
import { Clock, Film, Calendar } from "lucide-react";
import { Movie } from "@/types";
import Spinner from "@/components/ui/Spinner";

const fetcher = (url: string) => fetch(url).then((r) => r.json());

interface Countdown {
  days: number;
  hours: number;
  minutes: number;
  seconds: number;
}

function useCountdown(targetDate: string): Countdown {
  const [timeLeft, setTimeLeft] = useState<Countdown>({ days: 0, hours: 0, minutes: 0, seconds: 0 });

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

function CountdownUnit({ value, label }: { value: number; label: string }) {
  return (
    <div className="flex flex-col items-center">
      <div className="bg-brand-bg border border-brand-border rounded-xl w-16 h-16 flex items-center justify-center">
        <span className="text-2xl font-bold text-brand-primary">{String(value).padStart(2, "0")}</span>
      </div>
      <span className="text-brand-muted text-xs mt-1">{label}</span>
    </div>
  );
}

function MovieCard({ movie }: { movie: Movie }) {
  const countdown = useCountdown(movie.releaseDate);
  const releaseDate = new Date(movie.releaseDate).toLocaleDateString("en-GB", {
    weekday: "long",
    day: "numeric",
    month: "long",
    year: "numeric",
  });
  const isPast = new Date(movie.releaseDate) < new Date();

  return (
    <div className="cyber-panel rounded-2xl overflow-hidden hover:border-brand-primary/40 hover:shadow-xl hover:shadow-brand-primary/10 transition-all duration-300">
      {/* Poster area */}
      <div className="aspect-video bg-gradient-to-br from-brand-primary/20 via-brand-secondary/10 to-brand-card relative flex items-center justify-center overflow-hidden">
        {movie.backdropPath || movie.posterPath ? (
          <Image
            src={movie.backdropPath || movie.posterPath!}
            alt={movie.title}
            fill
            sizes="(max-width: 768px) 100vw, 33vw"
            className="object-cover"
          />
        ) : (
          <Film className="w-16 h-16 text-brand-primary/30" />
        )}
        <div className="absolute inset-0 bg-gradient-to-t from-brand-card via-brand-card/40 to-transparent" />
        <div className="absolute bottom-4 left-4 right-4">
          <h3 className="text-white font-bold text-xl mb-1">{movie.title}</h3>
          <div className="flex items-center gap-2">
            <Calendar className="w-3 h-3 text-brand-secondary" />
            <span className="text-brand-secondary text-xs">{releaseDate}</span>
          </div>
        </div>
        <div className="absolute top-3 right-3 flex flex-wrap gap-1">
          {movie.genres.slice(0, 2).map((g) => (
            <span key={g} className="bg-brand-bg/80 text-brand-muted text-xs px-2 py-0.5 rounded-full backdrop-blur-sm">
              {g}
            </span>
          ))}
        </div>
      </div>

      <div className="p-5">
        <p className="text-brand-muted text-sm leading-relaxed mb-5 line-clamp-3">{movie.overview}</p>

        {isPast ? (
          <div className="flex items-center gap-2 text-green-400">
            <span className="w-2 h-2 bg-green-400 rounded-full" />
            <span className="font-semibold text-sm">Now Available</span>
          </div>
        ) : (
          <>
            <div className="flex items-center gap-2 mb-4">
              <Clock className="w-4 h-4 text-brand-accent" />
              <span className="text-brand-muted text-xs font-medium">Releasing in:</span>
            </div>
            <div className="flex items-center gap-3">
              <CountdownUnit value={countdown.days} label="Days" />
              <span className="text-brand-primary font-bold text-xl pb-4">:</span>
              <CountdownUnit value={countdown.hours} label="Hours" />
              <span className="text-brand-primary font-bold text-xl pb-4">:</span>
              <CountdownUnit value={countdown.minutes} label="Mins" />
              <span className="text-brand-primary font-bold text-xl pb-4">:</span>
              <CountdownUnit value={countdown.seconds} label="Secs" />
            </div>
          </>
        )}
      </div>
    </div>
  );
}

export default function ComingSoonPage() {
  const { data, isLoading } = useSWR<{ movies: Movie[] }>("/api/coming-soon", fetcher);
  const movies = data?.movies || [];

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-10">
      <div className="mb-10">
        <h1 className="text-4xl sm:text-5xl font-black text-white mb-3">
          Coming{" "}
          <span className="bg-gradient-to-r from-brand-secondary to-brand-primary bg-clip-text text-transparent">
            Soon
          </span>
        </h1>
        <p className="text-brand-muted text-lg">
          Upcoming movies and shows landing on Enktel IPTV. Set your reminders!
        </p>
      </div>

      {isLoading ? (
        <Spinner className="py-20" />
      ) : movies.length === 0 ? (
        <div className="text-center text-brand-muted py-20">
          <Clock className="w-12 h-12 mx-auto mb-4 opacity-30" />
          <p>No upcoming releases found.</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-6">
          {movies.map((movie) => (
            <MovieCard key={movie.id} movie={movie} />
          ))}
        </div>
      )}
    </div>
  );
}
