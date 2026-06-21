import { EPGProgram, Movie, TVShow, Testimonial, WhatsOnItem, UpcomingEvent } from "@/types";
import { channels } from "./channels";

function getTimeOffset(minutesFromNow: number): string {
  const d = new Date();
  d.setMinutes(d.getMinutes() + minutesFromNow);
  d.setSeconds(0, 0);
  return d.toISOString();
}

export const mockMovies: Movie[] = [
  { id: 1, title: "Dune: Part Two", overview: "Follow the mythic journey of Paul Atreides as he unites with Chani and the Fremen.", posterPath: null, backdropPath: null, releaseDate: "2024-03-01", rating: 8.4, popularity: 920, genres: ["Sci-Fi", "Adventure"], language: "en", type: "movie" },
  { id: 2, title: "Oppenheimer", overview: "The story of American scientist J. Robert Oppenheimer and his role in the development of the atomic bomb.", posterPath: null, backdropPath: null, releaseDate: "2023-07-21", rating: 8.9, popularity: 840, genres: ["Drama", "History"], language: "en", type: "movie" },
  { id: 3, title: "Poor Things", overview: "An extraordinary young woman brought back to life by the brilliant and unorthodox scientist Dr. Godwin Baxter.", posterPath: null, backdropPath: null, releaseDate: "2023-12-08", rating: 8.1, popularity: 310, genres: ["Sci-Fi", "Comedy", "Drama"], language: "en", type: "movie" },
  { id: 4, title: "The Zone of Interest", overview: "A Nazi officer and his wife try to build a dream life for their family in a house next to the Auschwitz concentration camp.", posterPath: null, backdropPath: null, releaseDate: "2024-02-02", rating: 7.9, popularity: 120, genres: ["Drama", "War"], language: "en", type: "movie" },
  { id: 5, title: "Past Lives", overview: "Two childhood friends reunite after many years apart.", posterPath: null, backdropPath: null, releaseDate: "2023-06-02", rating: 8.0, popularity: 180, genres: ["Drama", "Romance"], language: "en", type: "movie" },
  { id: 6, title: "Killers of the Flower Moon", overview: "Members of the Osage Nation are murdered under mysterious circumstances in the 1920s.", posterPath: null, backdropPath: null, releaseDate: "2023-10-20", rating: 7.7, popularity: 420, genres: ["Drama", "Crime", "History"], language: "en", type: "movie" },
  { id: 7, title: "Saltburn", overview: "A student at Oxford University finds himself drawn into the world of a charming and aristocratic classmate.", posterPath: null, backdropPath: null, releaseDate: "2023-11-17", rating: 7.5, popularity: 520, genres: ["Thriller", "Drama"], language: "en", type: "movie" },
  { id: 8, title: "Society of the Snow", overview: "A Uruguayan rugby team stranded on a snow-capped Andes mountain after a plane crash must take extreme measures.", posterPath: null, backdropPath: null, releaseDate: "2024-01-04", rating: 7.9, popularity: 260, genres: ["Drama", "Adventure", "History"], language: "en", type: "movie" },
  { id: 9, title: "The Holdovers", overview: "A curmudgeonly instructor at a New England prep school is forced to stay on campus over the holidays.", posterPath: null, backdropPath: null, releaseDate: "2023-10-27", rating: 8.2, popularity: 290, genres: ["Drama", "Comedy"], language: "en", type: "movie" },
  { id: 10, title: "American Fiction", overview: "A novelist fed up with the business of literature uses a pen name to write a book that propels him into the heart of the hypocrisy and madness he claims to disdain.", posterPath: null, backdropPath: null, releaseDate: "2023-12-15", rating: 7.8, popularity: 150, genres: ["Comedy", "Drama"], language: "en", type: "movie" },
  { id: 11, title: "Godzilla x Kong: The New Empire", overview: "Two ancient titans, Godzilla and Kong, clash in an epic battle as humans unravel their intertwined origins.", posterPath: null, backdropPath: null, releaseDate: "2024-03-29", rating: 6.5, popularity: 970, genres: ["Action", "Sci-Fi", "Adventure"], language: "en", type: "movie" },
  { id: 12, title: "Civil War", overview: "A journalist travels across a war-torn America to reach Washington D.C. before rebel forces close in.", posterPath: null, backdropPath: null, releaseDate: "2024-04-12", rating: 7.4, popularity: 460, genres: ["Action", "Drama", "Thriller"], language: "en", type: "movie" },
];

export const mockTVShows: TVShow[] = [
  { id: 101, title: "The Bear", overview: "A young chef from the fine dining world returns home to run his family's sandwich shop.", posterPath: null, backdropPath: null, firstAirDate: "2022-06-23", rating: 9.0, popularity: 520, genres: ["Drama", "Comedy"], language: "en", type: "tv" },
  { id: 102, title: "House of the Dragon", overview: "An internal succession war within House Targaryen at the height of its power.", posterPath: null, backdropPath: null, firstAirDate: "2022-08-21", rating: 8.4, popularity: 910, genres: ["Fantasy", "Drama"], language: "en", type: "tv" },
  { id: 103, title: "Shogun", overview: "When a mysterious European ship is found adrift in the waters of Japan, the pilot becomes entangled in feudal power struggles.", posterPath: null, backdropPath: null, firstAirDate: "2024-02-27", rating: 8.9, popularity: 670, genres: ["History", "Drama"], language: "en", type: "tv" },
  { id: 104, title: "The Last of Us", overview: "Joel and Ellie travel across a post-apocalyptic America.", posterPath: null, backdropPath: null, firstAirDate: "2023-01-15", rating: 8.8, popularity: 890, genres: ["Sci-Fi", "Drama", "Thriller"], language: "en", type: "tv" },
  { id: 105, title: "Succession", overview: "The Roy family are known for controlling the biggest media and entertainment company in the world.", posterPath: null, backdropPath: null, firstAirDate: "2018-06-03", rating: 9.3, popularity: 610, genres: ["Drama"], language: "en", type: "tv" },
  { id: 106, title: "True Detective: Night Country", overview: "When the long Arctic night falls in Ennis, Alaska, the town is plunged into darkness.", posterPath: null, backdropPath: null, firstAirDate: "2024-01-14", rating: 8.0, popularity: 470, genres: ["Crime", "Drama", "Mystery"], language: "en", type: "tv" },
  { id: 107, title: "Abbott Elementary", overview: "A group of dedicated, passionate teachers set in a Philadelphia public school.", posterPath: null, backdropPath: null, firstAirDate: "2021-12-07", rating: 8.2, popularity: 330, genres: ["Comedy"], language: "en", type: "tv" },
  { id: 108, title: "The Morning Show", overview: "A character study of the people who help Americans wake up in the morning.", posterPath: null, backdropPath: null, firstAirDate: "2019-11-01", rating: 7.9, popularity: 380, genres: ["Drama"], language: "en", type: "tv" },
];

export const mockUpcomingMovies: Movie[] = [
  { id: 201, title: "Inside Out 2", overview: "Joy, Sadness, Anger, Fear and Disgust must work together with new emotions when Riley becomes a teenager.", posterPath: null, backdropPath: null, releaseDate: "2024-06-14", rating: 0, popularity: 880, genres: ["Animation", "Comedy", "Family"], language: "en", type: "movie" },
  { id: 202, title: "Deadpool & Wolverine", overview: "Deadpool is rejected from the Avengers and needs to get a suit from the TVA.", posterPath: null, backdropPath: null, releaseDate: "2024-07-26", rating: 0, popularity: 990, genres: ["Action", "Comedy", "Sci-Fi"], language: "en", type: "movie" },
  { id: 203, title: "Alien: Romulus", overview: "Set between the events of Alien and Aliens, a group of young people on a distant world find themselves face to face with the most terrifying life form in the universe.", posterPath: null, backdropPath: null, releaseDate: "2024-08-16", rating: 0, popularity: 640, genres: ["Horror", "Sci-Fi", "Thriller"], language: "en", type: "movie" },
  { id: 204, title: "Gladiator II", overview: "Years after witnessing the death of the revered hero Maximus at the hands of the corrupt emperor Commodus.", posterPath: null, backdropPath: null, releaseDate: "2024-11-22", rating: 0, popularity: 870, genres: ["Action", "Drama", "Adventure"], language: "en", type: "movie" },
  { id: 205, title: "Wicked", overview: "The untold story of the witches of Oz.", posterPath: null, backdropPath: null, releaseDate: "2024-11-22", rating: 0, popularity: 720, genres: ["Drama", "Fantasy", "Musical"], language: "en", type: "movie" },
  { id: 206, title: "Venom: The Last Dance", overview: "Eddie and Venom are on the run and hunted by both of their worlds.", posterPath: null, backdropPath: null, releaseDate: "2024-10-25", rating: 0, popularity: 760, genres: ["Action", "Sci-Fi"], language: "en", type: "movie" },
];

export const mockTestimonials: Testimonial[] = [
  {
    id: "1",
    name: "Mate Kovačević",
    location: "München, Germany 🇩🇪",
    avatar: "MK",
    rating: 5,
    text: "Živim u Njemačkoj već 10 godina i Enktel mi je promijenio život. Konačno mogu gledati HRT i Nova TV kao da sam kod kuće u Splitu. Preporučujem svima!",
    plan: "Pro",
  },
  {
    id: "2",
    name: "Ivan Tomić",
    location: "London, UK 🇬🇧",
    avatar: "IT",
    rating: 5,
    text: "Watched the entire Croatian World Cup 2022 campaign on Enktel from my flat in London. Crystal clear 4K, zero buffering. Absolutely brilliant service!",
    plan: "Pro",
  },
  {
    id: "3",
    name: "Sarah M.",
    location: "Manchester, UK 🇬🇧",
    avatar: "SM",
    rating: 5,
    text: "Switched from another IPTV provider and the difference is night and day. All my Croatian channels, the EPG is perfect, and the 4K quality is stunning. Worth every penny.",
    plan: "Ultimate",
  },
  {
    id: "4",
    name: "Amir Hadžić",
    location: "Vienna, Austria 🇦🇹",
    avatar: "AH",
    rating: 5,
    text: "Mogu gledati sve bosanske i hrvatske kanale bez problema. Hayat TV, FTV, HRT — sve radi savršeno. Podrška je odlična, odmah mi pomogli s postavljanjem.",
    plan: "Pro",
  },
  {
    id: "5",
    name: "James O.",
    location: "Dublin, Ireland 🇮🇪",
    avatar: "JO",
    rating: 5,
    text: "I get every Premier League match, Champions League, and even Croatian HNL. The sports coverage is exceptional. Setup took 5 minutes on my Firestick.",
    plan: "Ultimate",
  },
  {
    id: "6",
    name: "Ana Petrović",
    location: "Zürich, Switzerland 🇨🇭",
    avatar: "AP",
    rating: 5,
    text: "Enktel je jedini IPTV servis koji mi daje sve što trebam — HRT, Nova TV, RTL, ali i Sky i BBC. Kvaliteta slike je izvrsna. Toplo preporučujem!",
    plan: "Pro",
  },
];

// Builds "what's on now" items from an already-fetched set of real
// programmes (see src/lib/epg.ts).
export function buildWhatsOn(allPrograms: EPGProgram[], now: Date = new Date()): WhatsOnItem[] {
  const items: WhatsOnItem[] = [];

  for (const channel of channels) {
    const channelPrograms = allPrograms.filter(
      (p) => p.channelId === channel.id
    );
    const currentProgram = channelPrograms.find(
      (p) => new Date(p.startTime) <= now && new Date(p.endTime) > now
    );
    if (!currentProgram) continue;

    const currentIndex = channelPrograms.indexOf(currentProgram);
    const nextProgram = channelPrograms[currentIndex + 1] || null;

    const start = new Date(currentProgram.startTime).getTime();
    const end = new Date(currentProgram.endTime).getTime();
    const nowMs = now.getTime();
    const progressPercent = Math.round(((nowMs - start) / (end - start)) * 100);

    items.push({ channel, currentProgram, nextProgram, progressPercent });
  }

  return items;
}

// Used only if the live TheSportsDB lookup (src/lib/sportsApi.ts) is
// unreachable - keeps the Upcoming Sports & PPV section populated rather
// than empty.
export function getMockUpcomingEvents(): UpcomingEvent[] {
  const events: UpcomingEvent[] = [
    { id: "ev-1", title: "Dinamo Zagreb vs. Hajduk Split", competition: "HNL", sport: "Football", emoji: "⚽", channel: "Arena Sport 1", startTime: getTimeOffset(95), isPPV: false, isLive: false },
    { id: "ev-2", title: "Arsenal vs. Liverpool", competition: "Premier League", sport: "Football", emoji: "🏆", channel: "Sky Sports Football", startTime: getTimeOffset(40), isPPV: false, isLive: false },
    { id: "ev-3", title: "UFC Fight Night: Main Card", competition: "UFC", sport: "Combat Sports", emoji: "🥊", channel: "Eurosport 1", startTime: getTimeOffset(260), isPPV: true, isLive: false },
    { id: "ev-4", title: "Manchester City vs. Real Madrid", competition: "Champions League", sport: "Football", emoji: "⚽", channel: "TNT Sports 1", startTime: getTimeOffset(1450), isPPV: false, isLive: false },
    { id: "ev-5", title: "Lakers vs. Celtics", competition: "NBA", sport: "Basketball", emoji: "🏀", channel: "Sky Sports Main Event", startTime: getTimeOffset(2030), isPPV: false, isLive: false },
    { id: "ev-6", title: "Bayern Munich vs. Borussia Dortmund", competition: "Bundesliga", sport: "Football", emoji: "⚽", channel: "TNT Sports 2", startTime: getTimeOffset(2880), isPPV: false, isLive: false },
    { id: "ev-7", title: "Monaco Grand Prix — Race Day", competition: "Formula 1", sport: "Motorsport", emoji: "🏎️", channel: "Sky Sports Main Event", startTime: getTimeOffset(4150), isPPV: false, isLive: false },
  ];

  return events.map((e) => ({
    ...e,
    isLive: new Date(e.startTime).getTime() <= Date.now(),
  }));
}
