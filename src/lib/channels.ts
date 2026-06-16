import { Channel } from "@/types";

export const channels: Channel[] = [
  // Sports
  { id: "bbc-sport", name: "BBC Sport", category: "Sports", logoUrl: "https://via.placeholder.com/80x80/6C63FF/FFFFFF?text=BBC", epgId: "bbc-sport.uk", isHD: true, country: "UK" },
  { id: "sky-sports-main", name: "Sky Sports Main Event", category: "Sports", logoUrl: "https://via.placeholder.com/80x80/6C63FF/FFFFFF?text=SKY", epgId: "sky-sports-main.uk", isHD: true, country: "UK" },
  { id: "sky-sports-football", name: "Sky Sports Football", category: "Sports", logoUrl: "https://via.placeholder.com/80x80/00D4FF/080B16?text=SSF", epgId: "sky-sports-football.uk", isHD: true, country: "UK" },
  { id: "sky-sports-cricket", name: "Sky Sports Cricket", category: "Sports", logoUrl: "https://via.placeholder.com/80x80/00D4FF/080B16?text=SSC", epgId: "sky-sports-cricket.uk", isHD: true, country: "UK" },
  { id: "bt-sport-1", name: "BT Sport 1", category: "Sports", logoUrl: "https://via.placeholder.com/80x80/6C63FF/FFFFFF?text=BT1", epgId: "bt-sport-1.uk", isHD: true, country: "UK" },
  { id: "bt-sport-2", name: "BT Sport 2", category: "Sports", logoUrl: "https://via.placeholder.com/80x80/6C63FF/FFFFFF?text=BT2", epgId: "bt-sport-2.uk", isHD: true, country: "UK" },
  { id: "eurosport-1", name: "Eurosport 1", category: "Sports", logoUrl: "https://via.placeholder.com/80x80/FF4757/FFFFFF?text=ES1", epgId: "eurosport-1.uk", isHD: true, country: "UK" },
  { id: "espn", name: "ESPN", category: "Sports", logoUrl: "https://via.placeholder.com/80x80/FF4757/FFFFFF?text=ESPN", epgId: "espn.us", isHD: true, country: "US" },

  // Movies
  { id: "sky-cinema-premiere", name: "Sky Cinema Premiere", category: "Movies", logoUrl: "https://via.placeholder.com/80x80/6C63FF/FFFFFF?text=SCP", epgId: "sky-cinema-premiere.uk", isHD: true, country: "UK" },
  { id: "sky-cinema-action", name: "Sky Cinema Action", category: "Movies", logoUrl: "https://via.placeholder.com/80x80/FF4757/FFFFFF?text=SCA", epgId: "sky-cinema-action.uk", isHD: true, country: "UK" },
  { id: "sky-cinema-comedy", name: "Sky Cinema Comedy", category: "Movies", logoUrl: "https://via.placeholder.com/80x80/00D4FF/080B16?text=SCC", epgId: "sky-cinema-comedy.uk", isHD: true, country: "UK" },
  { id: "sky-cinema-thriller", name: "Sky Cinema Thriller", category: "Movies", logoUrl: "https://via.placeholder.com/80x80/6C63FF/FFFFFF?text=SCT", epgId: "sky-cinema-thriller.uk", isHD: true, country: "UK" },
  { id: "film4", name: "Film4", category: "Movies", logoUrl: "https://via.placeholder.com/80x80/FF4757/FFFFFF?text=F4", epgId: "film4.uk", isHD: true, country: "UK" },
  { id: "hbo", name: "HBO", category: "Movies", logoUrl: "https://via.placeholder.com/80x80/00D4FF/080B16?text=HBO", epgId: "hbo.us", isHD: true, country: "US" },

  // News
  { id: "bbc-news", name: "BBC News", category: "News", logoUrl: "https://via.placeholder.com/80x80/FF4757/FFFFFF?text=BBC", epgId: "bbc-news.uk", isHD: true, country: "UK" },
  { id: "sky-news", name: "Sky News", category: "News", logoUrl: "https://via.placeholder.com/80x80/00D4FF/080B16?text=SKY", epgId: "sky-news.uk", isHD: true, country: "UK" },
  { id: "itv-news", name: "ITV News", category: "News", logoUrl: "https://via.placeholder.com/80x80/6C63FF/FFFFFF?text=ITV", epgId: "itv-news.uk", isHD: true, country: "UK" },
  { id: "channel4-news", name: "Channel 4 News", category: "News", logoUrl: "https://via.placeholder.com/80x80/FF4757/FFFFFF?text=CH4", epgId: "channel4-news.uk", isHD: true, country: "UK" },
  { id: "cnn-intl", name: "CNN International", category: "News", logoUrl: "https://via.placeholder.com/80x80/FF4757/FFFFFF?text=CNN", epgId: "cnn.us", isHD: true, country: "US" },
  { id: "bbc-world", name: "BBC World News", category: "News", logoUrl: "https://via.placeholder.com/80x80/FF4757/FFFFFF?text=BBCW", epgId: "bbc-world.uk", isHD: true, country: "UK" },

  // Entertainment
  { id: "bbc-one", name: "BBC One", category: "Entertainment", logoUrl: "https://via.placeholder.com/80x80/FF4757/FFFFFF?text=BBC1", epgId: "bbc-one.uk", isHD: true, country: "UK" },
  { id: "bbc-two", name: "BBC Two", category: "Entertainment", logoUrl: "https://via.placeholder.com/80x80/FF4757/FFFFFF?text=BBC2", epgId: "bbc-two.uk", isHD: true, country: "UK" },
  { id: "itv1", name: "ITV1", category: "Entertainment", logoUrl: "https://via.placeholder.com/80x80/6C63FF/FFFFFF?text=ITV1", epgId: "itv1.uk", isHD: true, country: "UK" },
  { id: "itv2", name: "ITV2", category: "Entertainment", logoUrl: "https://via.placeholder.com/80x80/6C63FF/FFFFFF?text=ITV2", epgId: "itv2.uk", isHD: true, country: "UK" },
  { id: "channel4", name: "Channel 4", category: "Entertainment", logoUrl: "https://via.placeholder.com/80x80/00D4FF/080B16?text=CH4", epgId: "channel4.uk", isHD: true, country: "UK" },
  { id: "channel5", name: "Channel 5", category: "Entertainment", logoUrl: "https://via.placeholder.com/80x80/00D4FF/080B16?text=CH5", epgId: "channel5.uk", isHD: true, country: "UK" },
  { id: "e4", name: "E4", category: "Entertainment", logoUrl: "https://via.placeholder.com/80x80/6C63FF/FFFFFF?text=E4", epgId: "e4.uk", isHD: true, country: "UK" },
  { id: "dave", name: "Dave", category: "Entertainment", logoUrl: "https://via.placeholder.com/80x80/00D4FF/080B16?text=DAVE", epgId: "dave.uk", isHD: false, country: "UK" },
  { id: "gold", name: "Gold", category: "Entertainment", logoUrl: "https://via.placeholder.com/80x80/FF4757/FFFFFF?text=GOLD", epgId: "gold.uk", isHD: false, country: "UK" },
  { id: "sky-one", name: "Sky One", category: "Entertainment", logoUrl: "https://via.placeholder.com/80x80/6C63FF/FFFFFF?text=SKY1", epgId: "sky-one.uk", isHD: true, country: "UK" },
  { id: "sky-max", name: "Sky Max", category: "Entertainment", logoUrl: "https://via.placeholder.com/80x80/6C63FF/FFFFFF?text=SKYM", epgId: "sky-max.uk", isHD: true, country: "UK" },

  // Kids
  { id: "cbbc", name: "CBBC", category: "Kids", logoUrl: "https://via.placeholder.com/80x80/FF4757/FFFFFF?text=CBBC", epgId: "cbbc.uk", isHD: true, country: "UK" },
  { id: "cbeebies", name: "CBeebies", category: "Kids", logoUrl: "https://via.placeholder.com/80x80/00D4FF/080B16?text=CBee", epgId: "cbeebies.uk", isHD: true, country: "UK" },
  { id: "cartoon-network", name: "Cartoon Network", category: "Kids", logoUrl: "https://via.placeholder.com/80x80/6C63FF/FFFFFF?text=CN", epgId: "cartoon-network.us", isHD: true, country: "US" },
  { id: "nick-jr", name: "Nick Jr.", category: "Kids", logoUrl: "https://via.placeholder.com/80x80/FF4757/FFFFFF?text=NICK", epgId: "nick-jr.uk", isHD: true, country: "UK" },
  { id: "disney-channel", name: "Disney Channel", category: "Kids", logoUrl: "https://via.placeholder.com/80x80/00D4FF/080B16?text=DISN", epgId: "disney.uk", isHD: true, country: "UK" },
  { id: "disney-jr", name: "Disney Junior", category: "Kids", logoUrl: "https://via.placeholder.com/80x80/00D4FF/080B16?text=DISJR", epgId: "disney-jr.uk", isHD: true, country: "UK" },

  // Documentary
  { id: "discovery", name: "Discovery Channel", category: "Documentary", logoUrl: "https://via.placeholder.com/80x80/00D4FF/080B16?text=DISC", epgId: "discovery.uk", isHD: true, country: "UK" },
  { id: "nat-geo", name: "National Geographic", category: "Documentary", logoUrl: "https://via.placeholder.com/80x80/FF4757/FFFFFF?text=NATG", epgId: "nat-geo.uk", isHD: true, country: "UK" },
  { id: "history", name: "History Channel", category: "Documentary", logoUrl: "https://via.placeholder.com/80x80/6C63FF/FFFFFF?text=HIST", epgId: "history.uk", isHD: true, country: "UK" },
  { id: "bbc-four", name: "BBC Four", category: "Documentary", logoUrl: "https://via.placeholder.com/80x80/FF4757/FFFFFF?text=BBC4", epgId: "bbc-four.uk", isHD: true, country: "UK" },
  { id: "eden", name: "Eden", category: "Documentary", logoUrl: "https://via.placeholder.com/80x80/00D4FF/080B16?text=EDEN", epgId: "eden.uk", isHD: false, country: "UK" },
  { id: "animal-planet", name: "Animal Planet", category: "Documentary", logoUrl: "https://via.placeholder.com/80x80/00D4FF/080B16?text=AP", epgId: "animal-planet.uk", isHD: true, country: "UK" },

  // Music
  { id: "mtv", name: "MTV", category: "Music", logoUrl: "https://via.placeholder.com/80x80/FF4757/FFFFFF?text=MTV", epgId: "mtv.uk", isHD: true, country: "UK" },
  { id: "mtv-base", name: "MTV Base", category: "Music", logoUrl: "https://via.placeholder.com/80x80/FF4757/FFFFFF?text=MTVB", epgId: "mtv-base.uk", isHD: true, country: "UK" },
  { id: "vh1", name: "VH1", category: "Music", logoUrl: "https://via.placeholder.com/80x80/6C63FF/FFFFFF?text=VH1", epgId: "vh1.uk", isHD: false, country: "UK" },
  { id: "kerrang", name: "Kerrang!", category: "Music", logoUrl: "https://via.placeholder.com/80x80/FF4757/FFFFFF?text=KERR", epgId: "kerrang.uk", isHD: false, country: "UK" },
  { id: "kiss", name: "Kiss", category: "Music", logoUrl: "https://via.placeholder.com/80x80/6C63FF/FFFFFF?text=KISS", epgId: "kiss.uk", isHD: false, country: "UK" },
  { id: "magic-tv", name: "Magic TV", category: "Music", logoUrl: "https://via.placeholder.com/80x80/00D4FF/080B16?text=MAGIC", epgId: "magic.uk", isHD: false, country: "UK" },
];

export function getChannelsByCategory(category: string): Channel[] {
  if (category === "All") return channels;
  return channels.filter((c) => c.category === category);
}

export function getChannelById(id: string): Channel | undefined {
  return channels.find((c) => c.id === id);
}

export const channelCategories = [
  "All",
  "Sports",
  "Movies",
  "News",
  "Entertainment",
  "Kids",
  "Documentary",
  "Music",
] as const;
