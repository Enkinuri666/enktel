import { Channel } from "@/types";

export const channels: Channel[] = [
  // ── Croatian & Balkan ──
  { id: "hrt-1", name: "HRT 1", category: "Croatian & Balkan", epgId: "hrt-1.hr", isHD: true, country: "HR" },
  { id: "hrt-2", name: "HRT 2", category: "Croatian & Balkan", epgId: "hrt-2.hr", isHD: true, country: "HR" },
  { id: "hrt-3", name: "HRT 3", category: "Croatian & Balkan", epgId: "hrt-3.hr", isHD: true, country: "HR" },
  { id: "hrt-4", name: "HRT 4", category: "Croatian & Balkan", epgId: "hrt-4.hr", isHD: true, country: "HR" },
  { id: "nova-tv", name: "Nova TV", category: "Croatian & Balkan", epgId: "nova-tv.hr", isHD: true, country: "HR" },
  { id: "rtl-hrvatska", name: "RTL Hrvatska", category: "Croatian & Balkan", epgId: "rtl.hr", isHD: true, country: "HR" },
  { id: "rtl-2", name: "RTL 2", category: "Croatian & Balkan", epgId: "rtl-2.hr", isHD: true, country: "HR" },
  { id: "doma-tv", name: "Doma TV", category: "Croatian & Balkan", epgId: "doma-tv.hr", isHD: true, country: "HR" },
  { id: "cmc-tv", name: "CMC TV", category: "Croatian & Balkan", epgId: "cmc.hr", isHD: false, country: "HR" },
  { id: "n1-info", name: "N1 Info", category: "Croatian & Balkan", epgId: "n1.hr", isHD: true, country: "HR" },
  { id: "24sata-tv", name: "24sata TV", category: "Croatian & Balkan", epgId: "24sata.hr", isHD: false, country: "HR" },
  { id: "arena-sport-1", name: "Arena Sport 1", category: "Croatian & Balkan", epgId: "arena-1.hr", isHD: true, country: "HR" },
  { id: "arena-sport-2", name: "Arena Sport 2", category: "Croatian & Balkan", epgId: "arena-2.hr", isHD: true, country: "HR" },
  { id: "sportklub-1", name: "SportKlub 1", category: "Croatian & Balkan", epgId: "sportklub-1.hr", isHD: true, country: "HR" },
  { id: "hayat-tv", name: "Hayat TV", category: "Croatian & Balkan", epgId: "hayat.ba", isHD: true, country: "BA" },
  { id: "ftv", name: "FTV", category: "Croatian & Balkan", epgId: "ftv.ba", isHD: false, country: "BA" },
  { id: "rts-1", name: "RTS 1", category: "Croatian & Balkan", epgId: "rts-1.rs", isHD: true, country: "RS" },
  { id: "pink", name: "Pink", category: "Croatian & Balkan", epgId: "pink.rs", isHD: true, country: "RS" },
  { id: "nova-s", name: "Nova S", category: "Croatian & Balkan", epgId: "nova-s.rs", isHD: true, country: "RS" },

  // ── Sports ──
  { id: "bbc-sport", name: "BBC Sport", category: "Sports", epgId: "bbc-sport.uk", isHD: true, country: "UK" },
  { id: "sky-sports-main", name: "Sky Sports Main Event", category: "Sports", epgId: "sky-sports-main.uk", isHD: true, country: "UK" },
  { id: "sky-sports-football", name: "Sky Sports Football", category: "Sports", epgId: "sky-sports-football.uk", isHD: true, country: "UK" },
  { id: "sky-sports-cricket", name: "Sky Sports Cricket", category: "Sports", epgId: "sky-sports-cricket.uk", isHD: true, country: "UK" },
  { id: "bt-sport-1", name: "BT Sport 1", category: "Sports", epgId: "bt-sport-1.uk", isHD: true, country: "UK" },
  { id: "bt-sport-2", name: "BT Sport 2", category: "Sports", epgId: "bt-sport-2.uk", isHD: true, country: "UK" },
  { id: "eurosport-1", name: "Eurosport 1", category: "Sports", epgId: "eurosport-1.uk", isHD: true, country: "UK" },
  { id: "espn", name: "ESPN", category: "Sports", epgId: "espn.us", isHD: true, country: "US" },

  // ── Movies ──
  { id: "sky-cinema-premiere", name: "Sky Cinema Premiere", category: "Movies", epgId: "sky-cinema-premiere.uk", isHD: true, country: "UK" },
  { id: "sky-cinema-action", name: "Sky Cinema Action", category: "Movies", epgId: "sky-cinema-action.uk", isHD: true, country: "UK" },
  { id: "sky-cinema-comedy", name: "Sky Cinema Comedy", category: "Movies", epgId: "sky-cinema-comedy.uk", isHD: true, country: "UK" },
  { id: "sky-cinema-thriller", name: "Sky Cinema Thriller", category: "Movies", epgId: "sky-cinema-thriller.uk", isHD: true, country: "UK" },
  { id: "film4", name: "Film4", category: "Movies", epgId: "film4.uk", isHD: true, country: "UK" },
  { id: "hbo", name: "HBO", category: "Movies", epgId: "hbo.us", isHD: true, country: "US" },

  // ── News ──
  { id: "bbc-news", name: "BBC News", category: "News", epgId: "bbc-news.uk", isHD: true, country: "UK" },
  { id: "sky-news", name: "Sky News", category: "News", epgId: "sky-news.uk", isHD: true, country: "UK" },
  { id: "itv-news", name: "ITV News", category: "News", epgId: "itv-news.uk", isHD: true, country: "UK" },
  { id: "channel4-news", name: "Channel 4 News", category: "News", epgId: "channel4-news.uk", isHD: true, country: "UK" },
  { id: "cnn-intl", name: "CNN International", category: "News", epgId: "cnn.us", isHD: true, country: "US" },
  { id: "bbc-world", name: "BBC World News", category: "News", epgId: "bbc-world.uk", isHD: true, country: "UK" },

  // ── Entertainment ──
  { id: "bbc-one", name: "BBC One", category: "Entertainment", epgId: "bbc-one.uk", isHD: true, country: "UK" },
  { id: "bbc-two", name: "BBC Two", category: "Entertainment", epgId: "bbc-two.uk", isHD: true, country: "UK" },
  { id: "itv1", name: "ITV1", category: "Entertainment", epgId: "itv1.uk", isHD: true, country: "UK" },
  { id: "itv2", name: "ITV2", category: "Entertainment", epgId: "itv2.uk", isHD: true, country: "UK" },
  { id: "channel4", name: "Channel 4", category: "Entertainment", epgId: "channel4.uk", isHD: true, country: "UK" },
  { id: "channel5", name: "Channel 5", category: "Entertainment", epgId: "channel5.uk", isHD: true, country: "UK" },
  { id: "e4", name: "E4", category: "Entertainment", epgId: "e4.uk", isHD: true, country: "UK" },
  { id: "dave", name: "Dave", category: "Entertainment", epgId: "dave.uk", isHD: false, country: "UK" },
  { id: "gold", name: "Gold", category: "Entertainment", epgId: "gold.uk", isHD: false, country: "UK" },
  { id: "sky-one", name: "Sky One", category: "Entertainment", epgId: "sky-one.uk", isHD: true, country: "UK" },
  { id: "sky-max", name: "Sky Max", category: "Entertainment", epgId: "sky-max.uk", isHD: true, country: "UK" },

  // ── Kids ──
  { id: "cbbc", name: "CBBC", category: "Kids", epgId: "cbbc.uk", isHD: true, country: "UK" },
  { id: "cbeebies", name: "CBeebies", category: "Kids", epgId: "cbeebies.uk", isHD: true, country: "UK" },
  { id: "cartoon-network", name: "Cartoon Network", category: "Kids", epgId: "cartoon-network.us", isHD: true, country: "US" },
  { id: "nick-jr", name: "Nick Jr.", category: "Kids", epgId: "nick-jr.uk", isHD: true, country: "UK" },
  { id: "disney-channel", name: "Disney Channel", category: "Kids", epgId: "disney.uk", isHD: true, country: "UK" },
  { id: "disney-jr", name: "Disney Junior", category: "Kids", epgId: "disney-jr.uk", isHD: true, country: "UK" },

  // ── Documentary ──
  { id: "discovery", name: "Discovery Channel", category: "Documentary", epgId: "discovery.uk", isHD: true, country: "UK" },
  { id: "nat-geo", name: "National Geographic", category: "Documentary", epgId: "nat-geo.uk", isHD: true, country: "UK" },
  { id: "history", name: "History Channel", category: "Documentary", epgId: "history.uk", isHD: true, country: "UK" },
  { id: "bbc-four", name: "BBC Four", category: "Documentary", epgId: "bbc-four.uk", isHD: true, country: "UK" },
  { id: "eden", name: "Eden", category: "Documentary", epgId: "eden.uk", isHD: false, country: "UK" },
  { id: "animal-planet", name: "Animal Planet", category: "Documentary", epgId: "animal-planet.uk", isHD: true, country: "UK" },

  // ── Music ──
  { id: "mtv", name: "MTV", category: "Music", epgId: "mtv.uk", isHD: true, country: "UK" },
  { id: "mtv-base", name: "MTV Base", category: "Music", epgId: "mtv-base.uk", isHD: true, country: "UK" },
  { id: "vh1", name: "VH1", category: "Music", epgId: "vh1.uk", isHD: false, country: "UK" },
  { id: "kerrang", name: "Kerrang!", category: "Music", epgId: "kerrang.uk", isHD: false, country: "UK" },
  { id: "kiss", name: "Kiss", category: "Music", epgId: "kiss.uk", isHD: false, country: "UK" },
  { id: "magic-tv", name: "Magic TV", category: "Music", epgId: "magic.uk", isHD: false, country: "UK" },
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
  "Croatian & Balkan",
  "Sports",
  "Movies",
  "News",
  "Entertainment",
  "Kids",
  "Documentary",
  "Music",
] as const;
