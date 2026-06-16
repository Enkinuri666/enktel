import axios from "axios";
import { Movie, TVShow } from "@/types";

const TMDB_BASE_URL = "https://api.themoviedb.org/3";
const TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p/w500";

const tmdbClient = axios.create({
  baseURL: TMDB_BASE_URL,
  params: {
    api_key: process.env.TMDB_API_KEY,
    language: "en-GB",
    region: "GB",
  },
});

const genreMap: Record<number, string> = {
  28: "Action",
  12: "Adventure",
  16: "Animation",
  35: "Comedy",
  80: "Crime",
  99: "Documentary",
  18: "Drama",
  10751: "Family",
  14: "Fantasy",
  36: "History",
  27: "Horror",
  10402: "Music",
  9648: "Mystery",
  10749: "Romance",
  878: "Sci-Fi",
  10770: "TV Movie",
  53: "Thriller",
  10752: "War",
  37: "Western",
  10759: "Action & Adventure",
  10762: "Kids",
  10763: "News",
  10764: "Reality",
  10765: "Sci-Fi & Fantasy",
  10766: "Soap",
  10767: "Talk",
  10768: "War & Politics",
};

function mapMovie(m: Record<string, unknown>): Movie {
  const genreNames = ((m.genre_ids as number[]) || []).map(
    (id: number) => genreMap[id] || "Unknown"
  );
  return {
    id: m.id as number,
    title: (m.title as string) || "",
    overview: (m.overview as string) || "",
    posterPath: m.poster_path ? `${TMDB_IMAGE_BASE}${m.poster_path}` : null,
    backdropPath: m.backdrop_path ? `${TMDB_IMAGE_BASE}${m.backdrop_path}` : null,
    releaseDate: (m.release_date as string) || "",
    rating: (m.vote_average as number) || 0,
    genres: genreNames,
    type: "movie",
  };
}

function mapTVShow(s: Record<string, unknown>): TVShow {
  const genreNames = ((s.genre_ids as number[]) || []).map(
    (id: number) => genreMap[id] || "Unknown"
  );
  return {
    id: s.id as number,
    title: (s.name as string) || "",
    overview: (s.overview as string) || "",
    posterPath: s.poster_path ? `${TMDB_IMAGE_BASE}${s.poster_path}` : null,
    backdropPath: s.backdrop_path ? `${TMDB_IMAGE_BASE}${s.backdrop_path}` : null,
    firstAirDate: (s.first_air_date as string) || "",
    rating: (s.vote_average as number) || 0,
    genres: genreNames,
    type: "tv",
  };
}

export async function fetchNowPlaying(): Promise<Movie[]> {
  const res = await tmdbClient.get("/movie/now_playing");
  return (res.data.results as Record<string, unknown>[]).slice(0, 20).map(mapMovie);
}

export async function fetchOnTheAir(): Promise<TVShow[]> {
  const res = await tmdbClient.get("/tv/on_the_air");
  return (res.data.results as Record<string, unknown>[]).slice(0, 20).map(mapTVShow);
}

export async function fetchUpcomingMovies(): Promise<Movie[]> {
  const res = await tmdbClient.get("/movie/upcoming");
  return (res.data.results as Record<string, unknown>[]).slice(0, 20).map(mapMovie);
}

export async function fetchAiringToday(): Promise<TVShow[]> {
  const res = await tmdbClient.get("/tv/airing_today");
  return (res.data.results as Record<string, unknown>[]).slice(0, 20).map(mapTVShow);
}
