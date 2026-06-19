import { EPGProgram, Movie, TVShow, Testimonial, WhatsOnItem, UpcomingEvent } from "@/types";
import { channels } from "./channels";

function getTimeOffset(minutesFromNow: number): string {
  const d = new Date();
  d.setMinutes(d.getMinutes() + minutesFromNow);
  d.setSeconds(0, 0);
  return d.toISOString();
}

interface ProgramTemplate {
  title: string;
  description: string;
  duration: number;
  category: string;
  rating?: string;
}

// Tiles a list of program templates back-to-back, looping as needed, to cover
// a rolling window from 3 hours in the past to 24 hours in the future,
// computed relative to `now` so the EPG never goes stale.
function tileSchedule(channelId: string, templates: ProgramTemplate[], now: Date): EPGProgram[] {
  const result: EPGProgram[] = [];
  let offset = -180;
  let i = 0;
  while (offset < 1440) {
    const t = templates[i % templates.length];
    const start = new Date(now.getTime() + offset * 60000);
    offset += t.duration;
    const end = new Date(now.getTime() + offset * 60000);
    result.push({
      id: `${channelId}-${i}`,
      channelId,
      title: t.title,
      description: t.description,
      startTime: start.toISOString(),
      endTime: end.toISOString(),
      category: t.category,
      rating: t.rating,
    });
    i++;
  }
  return result;
}

const curatedTemplates: Record<string, ProgramTemplate[]> = {
  "bbc-one": [
    { title: "Breakfast", description: "The latest news, sport, business and weather from the BBC.", duration: 120, category: "News" },
    { title: "Morning Live", description: "Magazine programme with expert advice, recipes, and lifestyle features.", duration: 60, category: "Entertainment" },
    { title: "Doctors", description: "Drama series set in a Midlands GP practice.", duration: 30, category: "Drama", rating: "PG" },
    { title: "BBC News at One", description: "The latest national and international news stories.", duration: 30, category: "News" },
    { title: "The One Show", description: "Magazine programme with celebrity guests and human interest stories.", duration: 30, category: "Entertainment" },
    { title: "EastEnders", description: "Life in Albert Square takes a dramatic turn.", duration: 30, category: "Drama", rating: "PG" },
    { title: "BBC News at Six", description: "National and international news.", duration: 30, category: "News" },
    { title: "Antiques Roadshow", description: "Experts value the nation's hidden treasures.", duration: 60, category: "Entertainment" },
  ],
  "bbc-two": [
    { title: "Flog It!", description: "Paul Martin travels the country looking for antiques to sell at auction.", duration: 60, category: "Entertainment" },
    { title: "Escape to the Country", description: "Helping house hunters find their dream rural retreat.", duration: 60, category: "Lifestyle" },
    { title: "Bargain Hunt", description: "Two teams compete to buy antiques and sell them at profit.", duration: 45, category: "Entertainment" },
    { title: "University Challenge", description: "Jeremy Paxman quizzes teams from UK universities.", duration: 30, category: "Entertainment" },
    { title: "Newsnight", description: "In-depth investigation and analysis of the stories behind the day's headlines.", duration: 45, category: "News" },
    { title: "The Culture Show", description: "Arts and culture magazine programme.", duration: 60, category: "Arts" },
    { title: "QI", description: "Stephen Fry hosts a quiz show celebrating ignorance.", duration: 30, category: "Entertainment" },
  ],
  "itv1": [
    { title: "Good Morning Britain", description: "News and current affairs programme.", duration: 120, category: "News" },
    { title: "Lorraine", description: "Chat show hosted by Lorraine Kelly.", duration: 60, category: "Entertainment" },
    { title: "This Morning", description: "Magazine show covering news, lifestyle and celebrity interviews.", duration: 120, category: "Entertainment" },
    { title: "Loose Women", description: "Female-led chat show discussing the day's hot topics.", duration: 60, category: "Entertainment" },
    { title: "ITV News", description: "National and international news.", duration: 30, category: "News" },
    { title: "Emmerdale", description: "The drama continues in the Dales.", duration: 30, category: "Drama", rating: "PG" },
    { title: "Coronation Street", description: "Life on the cobbles takes a dramatic turn.", duration: 60, category: "Drama", rating: "PG" },
    { title: "ITV News at Ten", description: "The day's top stories.", duration: 30, category: "News" },
  ],
  "channel4": [
    { title: "A Place in the Sun", description: "Couples search for their dream overseas property.", duration: 60, category: "Lifestyle" },
    { title: "Countdown", description: "Long-running letters and numbers game show.", duration: 60, category: "Entertainment" },
    { title: "Channel 4 News", description: "Award-winning news programme.", duration: 60, category: "News" },
    { title: "Hollyoaks", description: "The lives of young people living in Chester.", duration: 30, category: "Drama", rating: "PG" },
    { title: "Gogglebox", description: "Opinionated people watching and commenting on television.", duration: 60, category: "Entertainment" },
    { title: "The Great British Bake Off", description: "Amateur bakers compete in themed challenges.", duration: 75, category: "Entertainment" },
    { title: "Channel 4 Late News", description: "Evening news bulletin.", duration: 30, category: "News" },
  ],
  "sky-sports-main": [
    { title: "Football: Premier League Preview", description: "Preview of the weekend's Premier League action.", duration: 60, category: "Sports" },
    { title: "Cricket: County Championship", description: "Live county cricket from venues around England.", duration: 180, category: "Sports" },
    { title: "Soccer Saturday", description: "Live updates and goals from across the day's football matches.", duration: 120, category: "Sports" },
    { title: "Goals on Sunday", description: "All the goals and talking points from the Premier League.", duration: 90, category: "Sports" },
    { title: "Fantasy Football Club", description: "Tips and analysis for fantasy football managers.", duration: 30, category: "Sports" },
  ],
  "sky-sports-football": [
    { title: "EFL Championship Live: Leeds vs Sheffield Wed", description: "Live EFL Championship match from Elland Road.", duration: 150, category: "Sports" },
    { title: "Football: Lower League Action", description: "Highlights from across the football pyramid.", duration: 60, category: "Sports" },
    { title: "Premier League Legends", description: "Classic Premier League matches from the archives.", duration: 90, category: "Sports" },
    { title: "Women's Super League: Arsenal vs Chelsea", description: "Live WSL match from the Emirates Stadium.", duration: 120, category: "Sports" },
    { title: "The Debate", description: "Football pundits debate the biggest talking points.", duration: 60, category: "Sports" },
  ],
  "bbc-news": [
    { title: "BBC World News", description: "The latest international news and analysis.", duration: 60, category: "News" },
    { title: "Click", description: "Technology news and features.", duration: 30, category: "News" },
    { title: "HARDtalk", description: "In-depth interview programme.", duration: 30, category: "News" },
    { title: "BBC World News", description: "Breaking news and international stories.", duration: 60, category: "News" },
    { title: "Outside Source", description: "News programme drawing on BBC's worldwide network of journalists.", duration: 60, category: "News" },
    { title: "BBC World News", description: "Comprehensive world news coverage.", duration: 60, category: "News" },
  ],
  "sky-news": [
    { title: "Kay Burley at Breakfast", description: "Morning news with Kay Burley.", duration: 90, category: "News" },
    { title: "Ian King Live", description: "Business news and analysis.", duration: 60, category: "News" },
    { title: "Sky News Live", description: "Rolling news coverage.", duration: 120, category: "News" },
    { title: "The Story with Dermot Murnaghan", description: "In-depth look at the biggest story of the day.", duration: 60, category: "News" },
    { title: "Sky News Tonight", description: "Evening news analysis.", duration: 60, category: "News" },
  ],
  "discovery": [
    { title: "Gold Rush", description: "Miners battle to strike it rich in the Yukon.", duration: 60, category: "Documentary" },
    { title: "Deadliest Catch", description: "Crab fishermen brave the Bering Sea.", duration: 60, category: "Documentary" },
    { title: "How It's Made", description: "Exploring the manufacturing process of everyday objects.", duration: 30, category: "Documentary" },
    { title: "MythBusters", description: "Adam and Jamie test popular myths.", duration: 60, category: "Documentary" },
    { title: "Dirty Jobs", description: "Mike Rowe takes on America's dirtiest professions.", duration: 60, category: "Documentary" },
    { title: "Naked and Afraid", description: "Two strangers survive in the wild with nothing.", duration: 60, category: "Documentary" },
  ],
  "nat-geo": [
    { title: "National Geographic Specials", description: "Award-winning natural history documentary.", duration: 60, category: "Documentary" },
    { title: "Brain Games", description: "Exploring the science of human perception.", duration: 30, category: "Documentary" },
    { title: "Cosmos: A Spacetime Odyssey", description: "Neil deGrasse Tyson explores the universe.", duration: 60, category: "Documentary" },
    { title: "Gordon Ramsay: Uncharted", description: "Gordon travels to remote locations to discover local cuisine.", duration: 60, category: "Documentary" },
    { title: "Photographer", description: "Following award-winning photographers on assignment.", duration: 60, category: "Documentary" },
  ],
  "cbbc": [
    { title: "Danger Mouse", description: "The world's greatest secret agent saves the day.", duration: 15, category: "Kids" },
    { title: "Hey Duggee", description: "Duggee and the squirrel club go on adventures.", duration: 15, category: "Kids" },
    { title: "Horrible Histories", description: "Bringing history to life with gross facts and comedy.", duration: 30, category: "Kids" },
    { title: "Blue Peter", description: "The world's longest-running children's magazine show.", duration: 30, category: "Kids" },
    { title: "Newsround", description: "The latest news for younger viewers.", duration: 15, category: "Kids" },
    { title: "Tracy Beaker Returns", description: "Tracy navigates life in the care system.", duration: 30, category: "Kids", rating: "PG" },
    { title: "The Dumping Ground", description: "Life continues at Ashdene Ridge.", duration: 30, category: "Kids", rating: "PG" },
  ],
  "mtv": [
    { title: "MTV Hits", description: "The biggest music videos right now.", duration: 60, category: "Music" },
    { title: "Ridiculousness", description: "Rob Dyrdek and friends react to viral videos.", duration: 30, category: "Entertainment" },
    { title: "Jersey Shore: Family Vacation", description: "The gang reunites in Miami.", duration: 60, category: "Entertainment" },
    { title: "MTV Base Presents: Top 20", description: "The biggest urban music videos.", duration: 60, category: "Music" },
    { title: "Catfish: The TV Show", description: "Nev and Max help people who suspect online deception.", duration: 60, category: "Entertainment" },
  ],
  "sky-cinema-premiere": [
    { title: "Top Gun: Maverick", description: "After thirty years, Maverick is still pushing the envelope as a top naval aviator.", duration: 131, category: "Movies", rating: "12A" },
    { title: "The Batman", description: "Batman ventures into Gotham City's underworld when a sadistic killer targets Gotham's elite.", duration: 176, category: "Movies", rating: "15" },
    { title: "Avatar: The Way of Water", description: "Jake Sully lives with his newfound family formed on the planet of Pandora.", duration: 192, category: "Movies", rating: "12A" },
  ],
  "film4": [
    { title: "The Shawshank Redemption", description: "Two imprisoned men bond over years, finding solace and eventual redemption.", duration: 142, category: "Movies", rating: "15" },
    { title: "Goodfellas", description: "The story of Henry Hill and his life in the mob.", duration: 146, category: "Movies", rating: "18" },
    { title: "The Dark Knight", description: "When the menace known as the Joker wreaks havoc, Batman must accept the role of hero.", duration: 152, category: "Movies", rating: "12A" },
  ],
};

// Generic per-category templates so every channel — even ones without a
// hand-written schedule above — gets a full, populated rolling EPG.
const categoryFallbackTemplates: Record<string, ProgramTemplate[]> = {
  Sports: [
    { title: "Live Match Coverage", description: "Live coverage of today's biggest fixture, with build-up and analysis.", duration: 120, category: "Sports" },
    { title: "Sports Centre", description: "Rolling highlights and breaking sports news.", duration: 30, category: "Sports" },
    { title: "Matchday Live", description: "Pre-match build-up, team news and expert analysis.", duration: 60, category: "Sports" },
    { title: "Classic Encounters", description: "Re-live legendary moments from the archives.", duration: 60, category: "Sports" },
    { title: "The Locker Room", description: "Pundits break down the day's biggest talking points.", duration: 45, category: "Sports" },
  ],
  Movies: [
    { title: "Feature Film Premiere", description: "A premium feature film, presented uncut in 4K.", duration: 110, category: "Movies", rating: "15" },
    { title: "Movie Matinee", description: "A family-friendly film for the afternoon.", duration: 95, category: "Movies", rating: "PG" },
    { title: "Director's Spotlight", description: "An acclaimed film from a celebrated director.", duration: 130, category: "Movies", rating: "12A" },
    { title: "Late Night Feature", description: "A gripping late-night feature presentation.", duration: 115, category: "Movies", rating: "15" },
  ],
  News: [
    { title: "Morning Briefing", description: "Breaking news, weather, and business updates.", duration: 60, category: "News" },
    { title: "World Report", description: "International news and in-depth analysis.", duration: 30, category: "News" },
    { title: "Evening Bulletin", description: "The day's top stories from around the world.", duration: 30, category: "News" },
    { title: "Newsroom Live", description: "Rolling live news coverage.", duration: 60, category: "News" },
  ],
  Entertainment: [
    { title: "Primetime Special", description: "A celebrity-packed entertainment special.", duration: 60, category: "Entertainment" },
    { title: "The Talk Show", description: "Interviews and entertainment news with a live audience.", duration: 45, category: "Entertainment" },
    { title: "Game Night", description: "Contestants compete for cash prizes in this popular quiz show.", duration: 30, category: "Entertainment" },
    { title: "Reality Hour", description: "Drama unfolds in this hit reality series.", duration: 60, category: "Entertainment" },
  ],
  Kids: [
    { title: "Morning Cartoons", description: "A fun-filled block of animated adventures.", duration: 30, category: "Kids" },
    { title: "Adventure Time", description: "Young heroes embark on an exciting quest.", duration: 25, category: "Kids" },
    { title: "Storytime Friends", description: "Friendly characters share lessons and laughs.", duration: 20, category: "Kids" },
    { title: "Family Fun Hour", description: "Games and activities for the whole family.", duration: 30, category: "Kids" },
  ],
  Documentary: [
    { title: "Wonders of the World", description: "Exploring remarkable places and natural phenomena.", duration: 60, category: "Documentary" },
    { title: "Inside Story", description: "A deep dive into a fascinating real-world subject.", duration: 50, category: "Documentary" },
    { title: "Behind the Lens", description: "Award-winning documentary filmmaking.", duration: 60, category: "Documentary" },
    { title: "Science Unlocked", description: "Breaking down complex science for everyone.", duration: 45, category: "Documentary" },
  ],
  Music: [
    { title: "Top Chart Hits", description: "Counting down the biggest music videos right now.", duration: 60, category: "Music" },
    { title: "Live Sessions", description: "Acoustic performances from rising artists.", duration: 45, category: "Music" },
    { title: "Throwback Hour", description: "Classic hits from the last three decades.", duration: 60, category: "Music" },
  ],
};

function templatesForChannel(channelId: string, category: string): ProgramTemplate[] {
  return (
    curatedTemplates[channelId] ||
    categoryFallbackTemplates[category] ||
    categoryFallbackTemplates.Entertainment
  );
}

// Builds a fresh rolling EPG for every channel, relative to `now`, so the
// guide always has full coverage and is never stale.
export function generateLiveSchedule(now: Date = new Date()): EPGProgram[] {
  const programs: EPGProgram[] = [];
  for (const channel of channels) {
    const templates = templatesForChannel(channel.id, channel.category);
    programs.push(...tileSchedule(channel.id, templates, now));
  }
  return programs;
}

export const mockMovies: Movie[] = [
  { id: 1, title: "Dune: Part Two", overview: "Follow the mythic journey of Paul Atreides as he unites with Chani and the Fremen.", posterPath: null, backdropPath: null, releaseDate: "2024-03-01", rating: 8.4, genres: ["Sci-Fi", "Adventure"], type: "movie" },
  { id: 2, title: "Oppenheimer", overview: "The story of American scientist J. Robert Oppenheimer and his role in the development of the atomic bomb.", posterPath: null, backdropPath: null, releaseDate: "2023-07-21", rating: 8.9, genres: ["Drama", "History"], type: "movie" },
  { id: 3, title: "Poor Things", overview: "An extraordinary young woman brought back to life by the brilliant and unorthodox scientist Dr. Godwin Baxter.", posterPath: null, backdropPath: null, releaseDate: "2023-12-08", rating: 8.1, genres: ["Sci-Fi", "Comedy", "Drama"], type: "movie" },
  { id: 4, title: "The Zone of Interest", overview: "A Nazi officer and his wife try to build a dream life for their family in a house next to the Auschwitz concentration camp.", posterPath: null, backdropPath: null, releaseDate: "2024-02-02", rating: 7.9, genres: ["Drama", "War"], type: "movie" },
  { id: 5, title: "Past Lives", overview: "Two childhood friends reunite after many years apart.", posterPath: null, backdropPath: null, releaseDate: "2023-06-02", rating: 8.0, genres: ["Drama", "Romance"], type: "movie" },
  { id: 6, title: "Killers of the Flower Moon", overview: "Members of the Osage Nation are murdered under mysterious circumstances in the 1920s.", posterPath: null, backdropPath: null, releaseDate: "2023-10-20", rating: 7.7, genres: ["Drama", "Crime", "History"], type: "movie" },
  { id: 7, title: "Saltburn", overview: "A student at Oxford University finds himself drawn into the world of a charming and aristocratic classmate.", posterPath: null, backdropPath: null, releaseDate: "2023-11-17", rating: 7.5, genres: ["Thriller", "Drama"], type: "movie" },
  { id: 8, title: "Society of the Snow", overview: "A Uruguayan rugby team stranded on a snow-capped Andes mountain after a plane crash must take extreme measures.", posterPath: null, backdropPath: null, releaseDate: "2024-01-04", rating: 7.9, genres: ["Drama", "Adventure", "History"], type: "movie" },
  { id: 9, title: "The Holdovers", overview: "A curmudgeonly instructor at a New England prep school is forced to stay on campus over the holidays.", posterPath: null, backdropPath: null, releaseDate: "2023-10-27", rating: 8.2, genres: ["Drama", "Comedy"], type: "movie" },
  { id: 10, title: "American Fiction", overview: "A novelist fed up with the business of literature uses a pen name to write a book that propels him into the heart of the hypocrisy and madness he claims to disdain.", posterPath: null, backdropPath: null, releaseDate: "2023-12-15", rating: 7.8, genres: ["Comedy", "Drama"], type: "movie" },
  { id: 11, title: "Godzilla x Kong: The New Empire", overview: "Two ancient titans, Godzilla and Kong, clash in an epic battle as humans unravel their intertwined origins.", posterPath: null, backdropPath: null, releaseDate: "2024-03-29", rating: 6.5, genres: ["Action", "Sci-Fi", "Adventure"], type: "movie" },
  { id: 12, title: "Civil War", overview: "A journalist travels across a war-torn America to reach Washington D.C. before rebel forces close in.", posterPath: null, backdropPath: null, releaseDate: "2024-04-12", rating: 7.4, genres: ["Action", "Drama", "Thriller"], type: "movie" },
];

export const mockTVShows: TVShow[] = [
  { id: 101, title: "The Bear", overview: "A young chef from the fine dining world returns home to run his family's sandwich shop.", posterPath: null, backdropPath: null, firstAirDate: "2022-06-23", rating: 9.0, genres: ["Drama", "Comedy"], type: "tv" },
  { id: 102, title: "House of the Dragon", overview: "An internal succession war within House Targaryen at the height of its power.", posterPath: null, backdropPath: null, firstAirDate: "2022-08-21", rating: 8.4, genres: ["Fantasy", "Drama"], type: "tv" },
  { id: 103, title: "Shogun", overview: "When a mysterious European ship is found adrift in the waters of Japan, the pilot becomes entangled in feudal power struggles.", posterPath: null, backdropPath: null, firstAirDate: "2024-02-27", rating: 8.9, genres: ["History", "Drama"], type: "tv" },
  { id: 104, title: "The Last of Us", overview: "Joel and Ellie travel across a post-apocalyptic America.", posterPath: null, backdropPath: null, firstAirDate: "2023-01-15", rating: 8.8, genres: ["Sci-Fi", "Drama", "Thriller"], type: "tv" },
  { id: 105, title: "Succession", overview: "The Roy family are known for controlling the biggest media and entertainment company in the world.", posterPath: null, backdropPath: null, firstAirDate: "2018-06-03", rating: 9.3, genres: ["Drama"], type: "tv" },
  { id: 106, title: "True Detective: Night Country", overview: "When the long Arctic night falls in Ennis, Alaska, the town is plunged into darkness.", posterPath: null, backdropPath: null, firstAirDate: "2024-01-14", rating: 8.0, genres: ["Crime", "Drama", "Mystery"], type: "tv" },
  { id: 107, title: "Abbott Elementary", overview: "A group of dedicated, passionate teachers set in a Philadelphia public school.", posterPath: null, backdropPath: null, firstAirDate: "2021-12-07", rating: 8.2, genres: ["Comedy"], type: "tv" },
  { id: 108, title: "The Morning Show", overview: "A character study of the people who help Americans wake up in the morning.", posterPath: null, backdropPath: null, firstAirDate: "2019-11-01", rating: 7.9, genres: ["Drama"], type: "tv" },
];

export const mockUpcomingMovies: Movie[] = [
  { id: 201, title: "Inside Out 2", overview: "Joy, Sadness, Anger, Fear and Disgust must work together with new emotions when Riley becomes a teenager.", posterPath: null, backdropPath: null, releaseDate: "2024-06-14", rating: 0, genres: ["Animation", "Comedy", "Family"], type: "movie" },
  { id: 202, title: "Deadpool & Wolverine", overview: "Deadpool is rejected from the Avengers and needs to get a suit from the TVA.", posterPath: null, backdropPath: null, releaseDate: "2024-07-26", rating: 0, genres: ["Action", "Comedy", "Sci-Fi"], type: "movie" },
  { id: 203, title: "Alien: Romulus", overview: "Set between the events of Alien and Aliens, a group of young people on a distant world find themselves face to face with the most terrifying life form in the universe.", posterPath: null, backdropPath: null, releaseDate: "2024-08-16", rating: 0, genres: ["Horror", "Sci-Fi", "Thriller"], type: "movie" },
  { id: 204, title: "Gladiator II", overview: "Years after witnessing the death of the revered hero Maximus at the hands of the corrupt emperor Commodus.", posterPath: null, backdropPath: null, releaseDate: "2024-11-22", rating: 0, genres: ["Action", "Drama", "Adventure"], type: "movie" },
  { id: 205, title: "Wicked", overview: "The untold story of the witches of Oz.", posterPath: null, backdropPath: null, releaseDate: "2024-11-22", rating: 0, genres: ["Drama", "Fantasy", "Musical"], type: "movie" },
  { id: 206, title: "Venom: The Last Dance", overview: "Eddie and Venom are on the run and hunted by both of their worlds.", posterPath: null, backdropPath: null, releaseDate: "2024-10-25", rating: 0, genres: ["Action", "Sci-Fi"], type: "movie" },
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
    text: "Switched from another IPTV provider and the difference is night and day. 10,000+ channels, the EPG is perfect, and the 4K quality is stunning. Worth every penny.",
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

export function getMockWhatsOn(): WhatsOnItem[] {
  const now = new Date();
  const items: WhatsOnItem[] = [];
  const allPrograms = generateLiveSchedule(now);

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

export function getMockUpcomingEvents(): UpcomingEvent[] {
  const events: UpcomingEvent[] = [
    { id: "ev-1", title: "Croatia vs. Brazil — World Cup 2026", competition: "FIFA World Cup 2026", emoji: "🏆", channel: "HRT 1", startTime: getTimeOffset(95), isPPV: false, isLive: false },
    { id: "ev-2", title: "England vs. Argentina — World Cup 2026", competition: "FIFA World Cup 2026", emoji: "🏆", channel: "Sky Sports", startTime: getTimeOffset(40), isPPV: false, isLive: false },
    { id: "ev-3", title: "Canelo vs. Benavidez — World Title", competition: "Boxing PPV", emoji: "🥊", channel: "DAZN PPV", startTime: getTimeOffset(260), isPPV: true, isLive: false },
    { id: "ev-4", title: "Manchester City vs. Real Madrid", competition: "Champions League", emoji: "⚽", channel: "BT Sport", startTime: getTimeOffset(1450), isPPV: false, isLive: false },
    { id: "ev-5", title: "UFC 312: Title Unification Bout", competition: "UFC PPV", emoji: "🥋", channel: "ESPN+ PPV", startTime: getTimeOffset(2030), isPPV: true, isLive: false },
    { id: "ev-6", title: "Dinamo Zagreb vs. Hajduk Split", competition: "HNL", emoji: "⚽", channel: "Arena Sport 1", startTime: getTimeOffset(2880), isPPV: false, isLive: false },
    { id: "ev-7", title: "Verstappen vs. Norris — Race Day", competition: "Formula 1", emoji: "🏎️", channel: "Sky Sports F1", startTime: getTimeOffset(4150), isPPV: false, isLive: false },
  ];

  return events.map((e) => ({
    ...e,
    isLive: new Date(e.startTime).getTime() <= Date.now(),
  }));
}
