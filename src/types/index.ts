export interface Channel {
  id: string;
  name: string;
  category: ChannelCategory;
  logoUrl?: string;
  epgId: string;
  isHD: boolean;
  country: string;
}

export type ChannelCategory =
  | "All"
  | "Croatian & Balkan"
  | "Sports"
  | "Movies"
  | "News"
  | "Entertainment"
  | "Kids"
  | "Documentary"
  | "Music";

export interface EPGProgram {
  id: string;
  channelId: string;
  title: string;
  description: string;
  startTime: string;
  endTime: string;
  category: string;
  rating?: string;
  imageUrl?: string;
  source?: "live";
}

export interface Movie {
  id: number;
  title: string;
  overview: string;
  posterPath: string | null;
  backdropPath: string | null;
  releaseDate: string;
  rating: number;
  popularity: number;
  genres: string[];
  language: string;
  type: "movie";
}

export interface TVShow {
  id: number;
  title: string;
  overview: string;
  posterPath: string | null;
  backdropPath: string | null;
  firstAirDate: string;
  rating: number;
  popularity: number;
  genres: string[];
  language: string;
  type: "tv";
}

export type MediaItem = Movie | TVShow;

export interface PricingPlan {
  id: string;
  name: string;
  price: number;
  annualPrice: number;
  currency: string;
  connections: number;
  channels: string;
  quality: string;
  catchUp: string;
  vod: boolean;
  prioritySupport: boolean;
  features: string[];
  highlighted: boolean;
}

export interface Subscription {
  id: string;
  userId: string;
  plan: PricingPlan;
  status: "active" | "expired" | "suspended";
  startDate: string;
  endDate: string;
  m3uUrl: string;
  epgUrl: string;
  connections: number;
}

export interface WhatsOnItem {
  channel: Channel;
  currentProgram: EPGProgram;
  nextProgram: EPGProgram | null;
  progressPercent: number;
}

export interface UpcomingEvent {
  id: string;
  title: string;
  competition: string;
  sport: string;
  emoji: string;
  channel: string;
  startTime: string;
  isPPV: boolean;
  isLive: boolean;
}

export interface Testimonial {
  id: string;
  name: string;
  location: string;
  rating: number;
  text: string;
  plan: string;
  avatar: string;
}

export interface EPGDay {
  date: string;
  label: string;
}
