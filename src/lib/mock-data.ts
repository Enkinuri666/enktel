import { EPGProgram, Movie, TVShow, Testimonial, WhatsOnItem, UpcomingEvent } from "@/types";
import { channels } from "./channels";

function getTimeOffset(minutesFromNow: number): string {
  const d = new Date();
  d.setMinutes(d.getMinutes() + minutesFromNow);
  d.setSeconds(0, 0);
  return d.toISOString();
}

// A program slot anchored to a real wall-clock time of day (24h). Each slot
// runs until the next slot begins, so morning shows air in the morning,
// the evening news airs in the evening, and primetime is primetime — the
// guide always reflects a believable real schedule for the current time.
interface DaySlot {
  h: number;
  m?: number;
  title: string;
  description: string;
  category: string;
  rating?: string;
}

// Expands a channel's day-part slots into concrete programmes for a single
// calendar day. The final slot wraps overnight into the next day's first
// slot, giving seamless 24h coverage with no gaps.
function buildDay(channelId: string, slots: DaySlot[], day: Date): EPGProgram[] {
  // Order by wall-clock time so after-midnight slots sit at the start of the
  // day and the final evening slot wraps cleanly overnight into the next
  // day's first programme — no gaps, no negative-length cells.
  const ordered = [...slots].sort(
    (a, b) => (a.h * 60 + (a.m || 0)) - (b.h * 60 + (b.m || 0))
  );

  const result: EPGProgram[] = [];
  for (let i = 0; i < ordered.length; i++) {
    const slot = ordered[i];
    const start = new Date(day);
    start.setHours(slot.h, slot.m || 0, 0, 0);

    const next = ordered[(i + 1) % ordered.length];
    const end = new Date(day);
    if (i === ordered.length - 1) end.setDate(end.getDate() + 1);
    end.setHours(next.h, next.m || 0, 0, 0);

    result.push({
      id: `${channelId}-${day.toISOString().slice(0, 10)}-${i}`,
      channelId,
      title: slot.title,
      description: slot.description,
      startTime: start.toISOString(),
      endTime: end.toISOString(),
      category: slot.category,
      rating: slot.rating,
      source: "simulated",
    });
  }
  return result;
}

const curatedSchedules: Record<string, DaySlot[]> = {
  // ── Croatian flagships ──
  "hrt-1": [
    { h: 6, title: "Dobro jutro, Hrvatska", description: "Informativno-zabavni jutarnji program s aktualnim temama, gostima i vremenskom prognozom.", category: "News" },
    { h: 8, m: 30, title: "Vijesti", description: "Pregled najnovijih domaćih i svjetskih vijesti.", category: "News" },
    { h: 8, m: 45, title: "Hrvatska uživo", description: "Reportaže i priče iz svih krajeva Hrvatske.", category: "News" },
    { h: 10, m: 30, title: "Kod nas doma", description: "Lifestyle magazin o domu, kuhinji i vrtu.", category: "Entertainment" },
    { h: 12, title: "Dnevnik 1", description: "Središnja dnevna informativna emisija HRT-a.", category: "News" },
    { h: 12, m: 40, title: "Sve u 7!", description: "Popularni kviz znanja s vrijednim nagradama.", category: "Entertainment" },
    { h: 14, m: 10, title: "Tema dana", description: "Detaljna analiza najvažnije priče dana.", category: "News" },
    { h: 15, title: "Popodne s nama", description: "Opušteni popodnevni magazin uz glazbu i goste.", category: "Entertainment" },
    { h: 17, title: "Reporteri", description: "Istraživački novinarski serijal.", category: "Documentary" },
    { h: 18, title: "Lijepom našom", description: "Glazbeno-putopisna emisija iz hrvatskih krajeva.", category: "Music" },
    { h: 19, m: 30, title: "Dnevnik", description: "Središnja informativna emisija.", category: "News" },
    { h: 20, m: 10, title: "Domaća dramska serija", description: "Nova epizoda popularne domaće dramske serije.", category: "Drama", rating: "12" },
    { h: 21, m: 50, title: "Otvoreno", description: "Aktualni politički talk-show.", category: "News" },
    { h: 23, title: "Dnevnik 3", description: "Pregled dana i najava sutrašnjih događaja.", category: "News" },
    { h: 23, m: 40, title: "Filmski maraton", description: "Cjelovečernji igrani film.", category: "Movies", rating: "15" },
    { h: 1, m: 30, title: "Noćni glazbeni program", description: "Glazbeni spotovi do jutra.", category: "Music" },
  ],
  "hrt-2": [
    { h: 6, title: "Crtani filmovi", description: "Jutarnji blok animiranih serija za najmlađe.", category: "Kids" },
    { h: 8, title: "Školski program", description: "Obrazovni sadržaj za učenike i studente.", category: "Documentary" },
    { h: 9, m: 30, title: "Dokumentarac", description: "Strani dokumentarni film.", category: "Documentary" },
    { h: 11, title: "Prijenos: Hrvatski sabor", description: "Izravni prijenos saborske rasprave.", category: "News" },
    { h: 13, title: "Filmski klasik", description: "Klasik svjetske kinematografije.", category: "Movies", rating: "12" },
    { h: 15, title: "Reprize serija", description: "Omiljene epizode domaćih i stranih serija.", category: "Drama" },
    { h: 17, m: 30, title: "Košarka: Prijenos uživo", description: "Izravni prijenos košarkaške utakmice ABA lige.", category: "Sports" },
    { h: 19, m: 30, title: "Glazbeni specijal", description: "Koncertni program i glazbene zvijezde.", category: "Music" },
    { h: 20, title: "Nogomet: HNL uživo", description: "Izravni prijenos utakmice SuperSport HNL-a.", category: "Sports" },
    { h: 22, title: "Pregled kola", description: "Saetak i analiza nogometnog kola.", category: "Sports" },
    { h: 23, title: "Filmski program", description: "Igrani film kasno navečer.", category: "Movies", rating: "15" },
    { h: 1, title: "Noćni program", description: "Reprizni sadržaj.", category: "Entertainment" },
  ],
  "nova-tv": [
    { h: 6, m: 15, title: "Crtani filmovi", description: "Jutarnji crtani program za djecu.", category: "Kids" },
    { h: 8, title: "IN magazin", description: "Showbiz vijesti iz Hrvatske i svijeta.", category: "Entertainment" },
    { h: 9, title: "Turske serije", description: "Omiljene turske dramske serije.", category: "Drama", rating: "12" },
    { h: 12, title: "Razvedi me", description: "Domaći reality sudski program.", category: "Entertainment" },
    { h: 13, title: "IN magazin", description: "Popodnevno izdanje showbiz magazina.", category: "Entertainment" },
    { h: 14, title: "Walker", description: "Akcijska dramska serija.", category: "Drama", rating: "12" },
    { h: 16, title: "Provjereno", description: "Istraživačka novinarska emisija.", category: "News" },
    { h: 17, title: "Vijesti Nove TV", description: "Aktualne dnevne vijesti.", category: "News" },
    { h: 17, m: 25, title: "Na granici", description: "Domaća dramska serija.", category: "Drama", rating: "12" },
    { h: 19, m: 15, title: "Dnevnik Nove TV", description: "Središnja informativna emisija Nove TV.", category: "News" },
    { h: 20, m: 5, title: "Supertalent", description: "Spektakularni show talenata.", category: "Entertainment" },
    { h: 22, title: "Red Carpet", description: "Magazin s crvenog tepiha.", category: "Entertainment" },
    { h: 23, title: "Film: Premijera", description: "Premijerni igrani film.", category: "Movies", rating: "15" },
    { h: 1, title: "Astro show", description: "Noćni interaktivni program.", category: "Entertainment" },
  ],
  "rtl-hrvatska": [
    { h: 6, title: "Jutarnje crtane serije", description: "Animirani program za najmlađe.", category: "Kids" },
    { h: 8, title: "RTL Danas", description: "Jutarnje izdanje informativne emisije.", category: "News" },
    { h: 9, title: "Exkluziv Tabloid", description: "Svijet poznatih i estrade.", category: "Entertainment" },
    { h: 10, title: "Ljubav je na selu", description: "Popularni reality o traženju ljubavi.", category: "Entertainment" },
    { h: 12, title: "Krv nije voda", description: "Domaća dramska serija.", category: "Drama", rating: "12" },
    { h: 13, title: "Turska serija", description: "Popodnevna dramska serija.", category: "Drama", rating: "12" },
    { h: 15, title: "Punom parom", description: "Kulinarski natjecateljski show.", category: "Entertainment" },
    { h: 16, title: "RTL Danas", description: "Popodnevne vijesti.", category: "News" },
    { h: 17, title: "Večera za 5", description: "Gastronomski reality.", category: "Entertainment" },
    { h: 18, title: "Exkluziv", description: "Večernji showbiz magazin.", category: "Entertainment" },
    { h: 19, title: "RTL Direkt", description: "Večernji informativni program.", category: "News" },
    { h: 20, title: "Big Brother", description: "Najgledaniji reality show u izravnom prijenosu.", category: "Entertainment" },
    { h: 22, title: "Film", description: "Cjelovečernji igrani film.", category: "Movies", rating: "15" },
    { h: 0, m: 30, title: "Noćni program", description: "Reprizni sadržaj.", category: "Entertainment" },
  ],
  "doma-tv": [
    { h: 6, title: "Telenovela", description: "Jutarnja epizoda latino telenovele.", category: "Drama" },
    { h: 8, title: "Zauvijek susjedi", description: "Popularna humoristična serija.", category: "Entertainment" },
    { h: 10, title: "Latino serija", description: "Dramska serija s puno strasti.", category: "Drama", rating: "12" },
    { h: 12, title: "Kobra 11", description: "Njemačka akcijska serija.", category: "Drama", rating: "12" },
    { h: 14, title: "Walker, Texas Ranger", description: "Klasična akcijska serija.", category: "Drama", rating: "12" },
    { h: 16, title: "Telenovela", description: "Popodnevna telenovela.", category: "Drama" },
    { h: 18, title: "Zauvijek susjedi", description: "Večernje izdanje humoristične serije.", category: "Entertainment" },
    { h: 19, title: "Latino serija", description: "Nova epizoda omiljene serije.", category: "Drama", rating: "12" },
    { h: 20, title: "Film tjedna", description: "Igrani film u glavnom terminu.", category: "Movies", rating: "15" },
    { h: 22, title: "Krimi serija", description: "Napeta kriminalistička serija.", category: "Drama", rating: "15" },
    { h: 0, title: "Noćni program", description: "Reprize serija.", category: "Entertainment" },
  ],
  "cmc-tv": [
    { h: 6, title: "CMC Express", description: "Jutarnji blok glazbenih spotova.", category: "Music" },
    { h: 9, title: "CMC Top 20", description: "Ljestvica najslušanijih domaćih hitova.", category: "Music" },
    { h: 12, title: "Glazbene želje", description: "Spotovi po željama gledatelja.", category: "Music" },
    { h: 15, title: "Domaći hitovi", description: "Najbolje iz hrvatske zabavne glazbe.", category: "Music" },
    { h: 18, title: "CMC Cafe", description: "Glazbeno-zabavna emisija uz goste.", category: "Music" },
    { h: 20, title: "Koncert: Klapske večeri", description: "Snimka koncerta dalmatinskih klapa.", category: "Music" },
    { h: 22, title: "Noćni program", description: "Zabavna glazba do jutra.", category: "Music" },
  ],
  "n1-info": [
    { h: 6, title: "Novi dan", description: "Jutarnji informativni program N1 televizije.", category: "News" },
    { h: 9, title: "N1 Studio uživo", description: "Vijesti i analize tijekom prijepodneva.", category: "News" },
    { h: 11, title: "Pressing", description: "Politički talk-show s aktualnim gostima.", category: "News" },
    { h: 13, title: "Vijesti u 13", description: "Središnje podnevne vijesti.", category: "News" },
    { h: 14, title: "Newsroom", description: "Rolling vijesti iz regije i svijeta.", category: "News" },
    { h: 17, title: "Novi dan: Popodne", description: "Popodnevni informativni blok.", category: "News" },
    { h: 18, title: "Dnevnik u 18", description: "Glavna informativna emisija.", category: "News" },
    { h: 19, title: "Intervju tjedna", description: "Razgovor s važnim sugovornikom.", category: "News" },
    { h: 20, title: "Pregled dana", description: "Saetak najvažnijih događaja.", category: "News" },
    { h: 22, title: "Vijesti", description: "Večernje vijesti.", category: "News" },
    { h: 0, title: "Najava dana", description: "Pregled tema za sutra i reprize.", category: "News" },
  ],
  "arena-sport-1": [
    { h: 6, title: "Sportski pregled", description: "Jutarnji pregled sportskih događaja.", category: "Sports" },
    { h: 8, title: "Klasici: Liga prvaka", description: "Nezaboravne utakmice Lige prvaka.", category: "Sports" },
    { h: 10, title: "Magazin: Serie A", description: "Tjedni magazin talijanskog nogometa.", category: "Sports" },
    { h: 12, title: "Najava kola HNL", description: "Pregled i najava nogometnog kola.", category: "Sports" },
    { h: 13, title: "Rukomet: Prijenos uživo", description: "Izravni prijenos rukometne utakmice.", category: "Sports" },
    { h: 15, title: "Studio: Premier League", description: "Analiza i najava engleskog nogometa.", category: "Sports" },
    { h: 16, title: "Nogomet: Premier League uživo", description: "Izravni prijenos utakmice Premier lige.", category: "Sports" },
    { h: 18, m: 30, title: "Studio: HNL", description: "Pretkutakmična analiza derbija.", category: "Sports" },
    { h: 19, title: "Nogomet: Dinamo – Hajduk", description: "Izravni prijenos vječnog derbija SuperSport HNL-a.", category: "Sports" },
    { h: 21, title: "Pregled kola", description: "Golovi i analiza odigranog kola.", category: "Sports" },
    { h: 22, title: "Boks: Borilački spektakl", description: "Prijenos boksačke priredbe.", category: "Sports" },
    { h: 0, title: "Sportski pregled dana", description: "Saetak dana u sportu.", category: "Sports" },
  ],

  // ── UK flagships ──
  "bbc-one": [
    { h: 6, title: "BBC Breakfast", description: "The latest news, sport, business and weather.", category: "News" },
    { h: 9, m: 15, title: "Morning Live", description: "Magazine programme with expert advice and lifestyle features.", category: "Entertainment" },
    { h: 10, title: "Homes Under the Hammer", description: "Buyers transform run-down auction properties.", category: "Entertainment" },
    { h: 11, m: 15, title: "Bargain Hunt", description: "Two teams hunt for antiques to sell at a profit.", category: "Entertainment" },
    { h: 13, title: "BBC News at One", description: "The latest national and international news.", category: "News" },
    { h: 13, m: 45, title: "Doctors", description: "Drama set in a Midlands GP practice.", category: "Drama", rating: "PG" },
    { h: 14, m: 15, title: "Escape to the Country", description: "House hunters search for their rural dream home.", category: "Entertainment" },
    { h: 16, m: 30, title: "The Bidding Room", description: "Sellers pitch their antiques to expert dealers.", category: "Entertainment" },
    { h: 17, m: 15, title: "Pointless", description: "Quiz show hunting for the most obscure correct answers.", category: "Entertainment" },
    { h: 18, title: "BBC News at Six", description: "National and international news.", category: "News" },
    { h: 19, title: "The One Show", description: "Magazine show with celebrity guests.", category: "Entertainment" },
    { h: 19, m: 30, title: "EastEnders", description: "Life in Albert Square takes a dramatic turn.", category: "Drama", rating: "PG" },
    { h: 20, title: "Primetime Drama", description: "A gripping new episode in the evening's headline drama.", category: "Drama", rating: "15" },
    { h: 22, title: "BBC News at Ten", description: "The day's top stories.", category: "News" },
    { h: 22, m: 30, title: "Match of the Day", description: "Premier League highlights and analysis.", category: "Sports" },
    { h: 0, title: "Weather for the Week Ahead", description: "Overnight news and weather.", category: "News" },
  ],
  "sky-sports-main": [
    { h: 6, title: "Good Morning Sports Fans", description: "Start the day with the biggest sports stories.", category: "Sports" },
    { h: 9, title: "Soccer AM", description: "Football entertainment and Premier League build-up.", category: "Sports" },
    { h: 11, title: "Saturday Social", description: "Football, gaming and online culture collide.", category: "Sports" },
    { h: 12, m: 30, title: "Live Build-Up", description: "Team news and analysis ahead of the lunchtime kick-off.", category: "Sports" },
    { h: 13, title: "Live EFL", description: "Live Championship football.", category: "Sports" },
    { h: 15, m: 15, title: "Gillette Soccer Saturday", description: "Live scores and reaction from across the day's fixtures.", category: "Sports" },
    { h: 17, m: 30, title: "Super Sunday Build-Up", description: "Pre-match analysis ahead of the big game.", category: "Sports" },
    { h: 18, title: "Live Premier League", description: "Live top-flight football in 4K Ultra HD.", category: "Sports" },
    { h: 20, m: 15, title: "Premier League Reaction", description: "Post-match interviews and expert analysis.", category: "Sports" },
    { h: 21, title: "The Football Show", description: "Debate and discussion on the day's big talking points.", category: "Sports" },
    { h: 22, m: 30, title: "Sky Sports News", description: "Round-the-clock sports headlines.", category: "Sports" },
    { h: 0, title: "Overnight Sports", description: "Highlights and rolling sports news.", category: "Sports" },
  ],
  "sky-news": [
    { h: 6, title: "Sunrise", description: "Sky News breakfast programme.", category: "News" },
    { h: 10, title: "Sky News Daily", description: "The morning's developing stories.", category: "News" },
    { h: 12, title: "Lunchtime Live", description: "Midday news and analysis.", category: "News" },
    { h: 14, title: "Afternoon Live", description: "Rolling afternoon news coverage.", category: "News" },
    { h: 17, title: "Sky News Drive", description: "The day's stories on the evening commute.", category: "News" },
    { h: 19, title: "The UK Tonight", description: "In-depth look at the UK's biggest stories.", category: "News" },
    { h: 20, title: "Sky News at Eight", description: "Evening news bulletin.", category: "News" },
    { h: 21, title: "The Politics Hub", description: "Westminster news and political debate.", category: "News" },
    { h: 22, title: "Sky News Tonight", description: "Late evening news analysis.", category: "News" },
    { h: 0, title: "Overnight Live", description: "Rolling overnight news coverage.", category: "News" },
  ],
};

// Generic day-part schedules so every channel — even ones without a
// hand-written guide above — reflects a realistic time of day.
const categorySchedules: Record<string, DaySlot[]> = {
  "Croatian & Balkan": [
    { h: 6, title: "Jutarnji program", description: "Informativno-zabavni jutarnji program.", category: "News" },
    { h: 9, title: "Magazin", description: "Lifestyle teme, gosti i savjeti.", category: "Entertainment" },
    { h: 11, title: "Domaća serija", description: "Reprize omiljenih domaćih serija.", category: "Drama" },
    { h: 13, title: "Vijesti", description: "Pregled dnevnih događaja.", category: "News" },
    { h: 13, m: 30, title: "Popodnevni program", description: "Zabavni sadržaj za cijelu obitelj.", category: "Entertainment" },
    { h: 16, title: "Turska serija", description: "Popularna dramska serija.", category: "Drama", rating: "12" },
    { h: 17, m: 30, title: "Reality show", description: "Najgledaniji reality format.", category: "Entertainment" },
    { h: 19, title: "Dnevnik", description: "Središnja informativna emisija.", category: "News" },
    { h: 20, title: "Primetime emisija", description: "Glavni večernji program.", category: "Entertainment" },
    { h: 22, title: "Film večeri", description: "Cjelovečernji igrani film.", category: "Movies", rating: "15" },
    { h: 0, title: "Noćni program", description: "Reprizni sadržaj do jutra.", category: "Entertainment" },
  ],
  Sports: [
    { h: 6, title: "Sports Breakfast", description: "The morning's headlines and build-up to the day's action.", category: "Sports" },
    { h: 9, title: "Morning Highlights", description: "The best of last night's sport.", category: "Sports" },
    { h: 11, title: "Classic Encounters", description: "Re-live legendary matches from the archives.", category: "Sports" },
    { h: 13, title: "Live Build-Up", description: "Team news and expert analysis ahead of the main event.", category: "Sports" },
    { h: 14, title: "Live Match Coverage", description: "Live coverage of today's biggest fixture.", category: "Sports" },
    { h: 16, m: 30, title: "Full-Time Analysis", description: "Reaction and tactical breakdown after the final whistle.", category: "Sports" },
    { h: 18, title: "Matchday Live", description: "Pre-match build-up to the evening kick-off.", category: "Sports" },
    { h: 19, m: 30, title: "Live Match Coverage", description: "Live evening fixture in 4K Ultra HD.", category: "Sports" },
    { h: 22, title: "The Debate", description: "Pundits break down the day's biggest talking points.", category: "Sports" },
    { h: 23, m: 30, title: "Sports Tonight", description: "Rolling highlights and breaking sports news.", category: "Sports" },
    { h: 1, title: "Overnight Sports", description: "Highlights through the night.", category: "Sports" },
  ],
  Movies: [
    { h: 6, title: "Morning Matinee", description: "An easy-watching film to start the day.", category: "Movies", rating: "PG" },
    { h: 8, title: "Family Feature", description: "A film the whole family can enjoy.", category: "Movies", rating: "PG" },
    { h: 10, title: "Comedy Classic", description: "A much-loved comedy favourite.", category: "Movies", rating: "12" },
    { h: 12, title: "Afternoon Feature", description: "A blockbuster for the afternoon.", category: "Movies", rating: "12" },
    { h: 14, m: 30, title: "Action Hour", description: "Edge-of-your-seat action and adventure.", category: "Movies", rating: "15" },
    { h: 16, m: 30, title: "Premiere Matinee", description: "A recent cinema release.", category: "Movies", rating: "12" },
    { h: 19, title: "Primetime Premiere", description: "Tonight's headline premiere, uncut in 4K.", category: "Movies", rating: "15" },
    { h: 21, title: "Feature Presentation", description: "An award-winning feature film.", category: "Movies", rating: "15" },
    { h: 23, m: 15, title: "Late Night Thriller", description: "A gripping late-night thriller.", category: "Movies", rating: "18" },
    { h: 1, m: 30, title: "Cult Cinema", description: "A cult favourite for the night owls.", category: "Movies", rating: "18" },
  ],
  News: [
    { h: 6, title: "Morning Briefing", description: "Breaking news, weather and business updates.", category: "News" },
    { h: 9, title: "Business Today", description: "The latest from the world's markets.", category: "News" },
    { h: 11, title: "World Report", description: "International news and in-depth analysis.", category: "News" },
    { h: 13, title: "News at One", description: "The day's top stories at lunchtime.", category: "News" },
    { h: 15, title: "Afternoon Live", description: "Rolling afternoon news coverage.", category: "News" },
    { h: 17, title: "The Brief", description: "A round-up of the day so far.", category: "News" },
    { h: 18, title: "Evening News", description: "Comprehensive evening bulletin.", category: "News" },
    { h: 20, title: "Tonight", description: "In-depth analysis of the day's biggest story.", category: "News" },
    { h: 22, title: "News at Ten", description: "The day's headlines and reaction.", category: "News" },
    { h: 0, title: "Overnight Live", description: "Rolling international news through the night.", category: "News" },
  ],
  Entertainment: [
    { h: 6, title: "Morning Magazine", description: "A bright start with news, chat and lifestyle.", category: "Entertainment" },
    { h: 9, title: "Lifestyle & Home", description: "Home, garden and cooking inspiration.", category: "Entertainment" },
    { h: 11, title: "Daytime Talk", description: "Topical chat with a live studio audience.", category: "Entertainment" },
    { h: 13, title: "Afternoon Drama", description: "A daytime drama serial.", category: "Drama", rating: "PG" },
    { h: 15, title: "Game Show", description: "Contestants compete for cash and prizes.", category: "Entertainment" },
    { h: 17, title: "Quiz Hour", description: "A fast-paced general knowledge quiz.", category: "Entertainment" },
    { h: 18, m: 30, title: "Early Evening News", description: "The day's news at a glance.", category: "News" },
    { h: 19, title: "Primetime Series", description: "Tonight's must-watch returning series.", category: "Entertainment" },
    { h: 21, title: "Featured Drama", description: "A gripping primetime drama.", category: "Drama", rating: "15" },
    { h: 23, title: "Late Night Talk", description: "Comedy and celebrity guests after dark.", category: "Entertainment" },
    { h: 1, title: "Overnight Replays", description: "Catch-up on the day's highlights.", category: "Entertainment" },
  ],
  Kids: [
    { h: 6, title: "Morning Cartoons", description: "A fun-filled block of animated adventures.", category: "Kids" },
    { h: 9, title: "Preschool Hour", description: "Gentle learning and play for little ones.", category: "Kids" },
    { h: 11, title: "Adventure Time", description: "Young heroes embark on an exciting quest.", category: "Kids" },
    { h: 13, title: "Cartoon Block", description: "Back-to-back animated favourites.", category: "Kids" },
    { h: 15, title: "After-School Toons", description: "The perfect line-up after a day at school.", category: "Kids" },
    { h: 17, title: "Family Movie", description: "An animated feature for all ages.", category: "Kids", rating: "PG" },
    { h: 19, title: "Bedtime Stories", description: "Calm tales to wind down the day.", category: "Kids" },
    { h: 20, title: "Teen Drama", description: "Drama and friendship for older kids.", category: "Kids", rating: "PG" },
    { h: 22, title: "Overnight Animation", description: "Quiet cartoons through the night.", category: "Kids" },
  ],
  Documentary: [
    { h: 6, title: "Sunrise Earth", description: "Stunning natural landscapes to start the day.", category: "Documentary" },
    { h: 8, title: "Wild Worlds", description: "Wildlife from the planet's remotest corners.", category: "Documentary" },
    { h: 10, title: "History Hour", description: "Bringing the past vividly to life.", category: "Documentary" },
    { h: 12, title: "Science Today", description: "Breaking down the science shaping our world.", category: "Documentary" },
    { h: 14, title: "Nature's Wonders", description: "Remarkable natural phenomena explored.", category: "Documentary" },
    { h: 16, title: "Engineering Marvels", description: "The stories behind humanity's greatest builds.", category: "Documentary" },
    { h: 18, title: "Inside Story", description: "A deep dive into a fascinating real-world subject.", category: "Documentary" },
    { h: 20, title: "Featured Documentary", description: "Tonight's award-winning feature documentary.", category: "Documentary" },
    { h: 22, title: "True Crime", description: "A gripping real-life investigation.", category: "Documentary", rating: "15" },
    { h: 0, title: "Late Night Docs", description: "Thought-provoking films through the night.", category: "Documentary" },
  ],
  Music: [
    { h: 6, title: "Wake Up Hits", description: "The biggest tracks to start your morning.", category: "Music" },
    { h: 9, title: "Throwback Mornings", description: "Classic hits from the last three decades.", category: "Music" },
    { h: 12, title: "Chart Countdown", description: "Counting down this week's biggest songs.", category: "Music" },
    { h: 15, title: "New Music Now", description: "The freshest releases and rising artists.", category: "Music" },
    { h: 18, title: "Drivetime Anthems", description: "Feel-good anthems for the journey home.", category: "Music" },
    { h: 20, title: "Live Sessions", description: "Exclusive live performances and acoustic sets.", category: "Music" },
    { h: 22, title: "Club Classics", description: "Dancefloor fillers to see in the night.", category: "Music" },
    { h: 0, title: "After Hours", description: "Laid-back tracks into the early hours.", category: "Music" },
  ],
};

function scheduleForChannel(channelId: string, category: string): DaySlot[] {
  return (
    curatedSchedules[channelId] ||
    categorySchedules[category] ||
    categorySchedules.Entertainment
  );
}

// Builds a time-of-day accurate EPG for every channel covering yesterday,
// today and tomorrow, so the rolling guide window always has full coverage
// and "what's on now" matches the real wall-clock time.
export function generateLiveSchedule(now: Date = new Date()): EPGProgram[] {
  const days = [-1, 0, 1].map((offset) => {
    const d = new Date(now);
    d.setDate(d.getDate() + offset);
    d.setHours(0, 0, 0, 0);
    return d;
  });

  const programs: EPGProgram[] = [];
  for (const channel of channels) {
    const slots = scheduleForChannel(channel.id, channel.category);
    for (const day of days) {
      programs.push(...buildDay(channel.id, slots, day));
    }
  }
  return programs;
}

export const mockMovies: Movie[] = [
  { id: 1, title: "Dune: Part Two", overview: "Follow the mythic journey of Paul Atreides as he unites with Chani and the Fremen.", posterPath: null, backdropPath: null, releaseDate: "2024-03-01", rating: 8.4, genres: ["Sci-Fi", "Adventure"], language: "en", type: "movie" },
  { id: 2, title: "Oppenheimer", overview: "The story of American scientist J. Robert Oppenheimer and his role in the development of the atomic bomb.", posterPath: null, backdropPath: null, releaseDate: "2023-07-21", rating: 8.9, genres: ["Drama", "History"], language: "en", type: "movie" },
  { id: 3, title: "Poor Things", overview: "An extraordinary young woman brought back to life by the brilliant and unorthodox scientist Dr. Godwin Baxter.", posterPath: null, backdropPath: null, releaseDate: "2023-12-08", rating: 8.1, genres: ["Sci-Fi", "Comedy", "Drama"], language: "en", type: "movie" },
  { id: 4, title: "The Zone of Interest", overview: "A Nazi officer and his wife try to build a dream life for their family in a house next to the Auschwitz concentration camp.", posterPath: null, backdropPath: null, releaseDate: "2024-02-02", rating: 7.9, genres: ["Drama", "War"], language: "en", type: "movie" },
  { id: 5, title: "Past Lives", overview: "Two childhood friends reunite after many years apart.", posterPath: null, backdropPath: null, releaseDate: "2023-06-02", rating: 8.0, genres: ["Drama", "Romance"], language: "en", type: "movie" },
  { id: 6, title: "Killers of the Flower Moon", overview: "Members of the Osage Nation are murdered under mysterious circumstances in the 1920s.", posterPath: null, backdropPath: null, releaseDate: "2023-10-20", rating: 7.7, genres: ["Drama", "Crime", "History"], language: "en", type: "movie" },
  { id: 7, title: "Saltburn", overview: "A student at Oxford University finds himself drawn into the world of a charming and aristocratic classmate.", posterPath: null, backdropPath: null, releaseDate: "2023-11-17", rating: 7.5, genres: ["Thriller", "Drama"], language: "en", type: "movie" },
  { id: 8, title: "Society of the Snow", overview: "A Uruguayan rugby team stranded on a snow-capped Andes mountain after a plane crash must take extreme measures.", posterPath: null, backdropPath: null, releaseDate: "2024-01-04", rating: 7.9, genres: ["Drama", "Adventure", "History"], language: "en", type: "movie" },
  { id: 9, title: "The Holdovers", overview: "A curmudgeonly instructor at a New England prep school is forced to stay on campus over the holidays.", posterPath: null, backdropPath: null, releaseDate: "2023-10-27", rating: 8.2, genres: ["Drama", "Comedy"], language: "en", type: "movie" },
  { id: 10, title: "American Fiction", overview: "A novelist fed up with the business of literature uses a pen name to write a book that propels him into the heart of the hypocrisy and madness he claims to disdain.", posterPath: null, backdropPath: null, releaseDate: "2023-12-15", rating: 7.8, genres: ["Comedy", "Drama"], language: "en", type: "movie" },
  { id: 11, title: "Godzilla x Kong: The New Empire", overview: "Two ancient titans, Godzilla and Kong, clash in an epic battle as humans unravel their intertwined origins.", posterPath: null, backdropPath: null, releaseDate: "2024-03-29", rating: 6.5, genres: ["Action", "Sci-Fi", "Adventure"], language: "en", type: "movie" },
  { id: 12, title: "Civil War", overview: "A journalist travels across a war-torn America to reach Washington D.C. before rebel forces close in.", posterPath: null, backdropPath: null, releaseDate: "2024-04-12", rating: 7.4, genres: ["Action", "Drama", "Thriller"], language: "en", type: "movie" },
];

export const mockTVShows: TVShow[] = [
  { id: 101, title: "The Bear", overview: "A young chef from the fine dining world returns home to run his family's sandwich shop.", posterPath: null, backdropPath: null, firstAirDate: "2022-06-23", rating: 9.0, genres: ["Drama", "Comedy"], language: "en", type: "tv" },
  { id: 102, title: "House of the Dragon", overview: "An internal succession war within House Targaryen at the height of its power.", posterPath: null, backdropPath: null, firstAirDate: "2022-08-21", rating: 8.4, genres: ["Fantasy", "Drama"], language: "en", type: "tv" },
  { id: 103, title: "Shogun", overview: "When a mysterious European ship is found adrift in the waters of Japan, the pilot becomes entangled in feudal power struggles.", posterPath: null, backdropPath: null, firstAirDate: "2024-02-27", rating: 8.9, genres: ["History", "Drama"], language: "en", type: "tv" },
  { id: 104, title: "The Last of Us", overview: "Joel and Ellie travel across a post-apocalyptic America.", posterPath: null, backdropPath: null, firstAirDate: "2023-01-15", rating: 8.8, genres: ["Sci-Fi", "Drama", "Thriller"], language: "en", type: "tv" },
  { id: 105, title: "Succession", overview: "The Roy family are known for controlling the biggest media and entertainment company in the world.", posterPath: null, backdropPath: null, firstAirDate: "2018-06-03", rating: 9.3, genres: ["Drama"], language: "en", type: "tv" },
  { id: 106, title: "True Detective: Night Country", overview: "When the long Arctic night falls in Ennis, Alaska, the town is plunged into darkness.", posterPath: null, backdropPath: null, firstAirDate: "2024-01-14", rating: 8.0, genres: ["Crime", "Drama", "Mystery"], language: "en", type: "tv" },
  { id: 107, title: "Abbott Elementary", overview: "A group of dedicated, passionate teachers set in a Philadelphia public school.", posterPath: null, backdropPath: null, firstAirDate: "2021-12-07", rating: 8.2, genres: ["Comedy"], language: "en", type: "tv" },
  { id: 108, title: "The Morning Show", overview: "A character study of the people who help Americans wake up in the morning.", posterPath: null, backdropPath: null, firstAirDate: "2019-11-01", rating: 7.9, genres: ["Drama"], language: "en", type: "tv" },
];

export const mockUpcomingMovies: Movie[] = [
  { id: 201, title: "Inside Out 2", overview: "Joy, Sadness, Anger, Fear and Disgust must work together with new emotions when Riley becomes a teenager.", posterPath: null, backdropPath: null, releaseDate: "2024-06-14", rating: 0, genres: ["Animation", "Comedy", "Family"], language: "en", type: "movie" },
  { id: 202, title: "Deadpool & Wolverine", overview: "Deadpool is rejected from the Avengers and needs to get a suit from the TVA.", posterPath: null, backdropPath: null, releaseDate: "2024-07-26", rating: 0, genres: ["Action", "Comedy", "Sci-Fi"], language: "en", type: "movie" },
  { id: 203, title: "Alien: Romulus", overview: "Set between the events of Alien and Aliens, a group of young people on a distant world find themselves face to face with the most terrifying life form in the universe.", posterPath: null, backdropPath: null, releaseDate: "2024-08-16", rating: 0, genres: ["Horror", "Sci-Fi", "Thriller"], language: "en", type: "movie" },
  { id: 204, title: "Gladiator II", overview: "Years after witnessing the death of the revered hero Maximus at the hands of the corrupt emperor Commodus.", posterPath: null, backdropPath: null, releaseDate: "2024-11-22", rating: 0, genres: ["Action", "Drama", "Adventure"], language: "en", type: "movie" },
  { id: 205, title: "Wicked", overview: "The untold story of the witches of Oz.", posterPath: null, backdropPath: null, releaseDate: "2024-11-22", rating: 0, genres: ["Drama", "Fantasy", "Musical"], language: "en", type: "movie" },
  { id: 206, title: "Venom: The Last Dance", overview: "Eddie and Venom are on the run and hunted by both of their worlds.", posterPath: null, backdropPath: null, releaseDate: "2024-10-25", rating: 0, genres: ["Action", "Sci-Fi"], language: "en", type: "movie" },
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

// Builds "what's on now" items from an already-fetched set of programmes
// (real, simulated, or a merge of both - see src/lib/epg.ts) so the same
// logic works regardless of where the programme data came from.
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
    { id: "ev-3", title: "UFC Fight Night: Main Card", competition: "UFC", sport: "Combat Sports", emoji: "🥊", channel: "ESPN", startTime: getTimeOffset(260), isPPV: true, isLive: false },
    { id: "ev-4", title: "Manchester City vs. Real Madrid", competition: "Champions League", sport: "Football", emoji: "⚽", channel: "BT Sport 1", startTime: getTimeOffset(1450), isPPV: false, isLive: false },
    { id: "ev-5", title: "Lakers vs. Celtics", competition: "NBA", sport: "Basketball", emoji: "🏀", channel: "ESPN", startTime: getTimeOffset(2030), isPPV: false, isLive: false },
    { id: "ev-6", title: "Bayern Munich vs. Borussia Dortmund", competition: "Bundesliga", sport: "Football", emoji: "⚽", channel: "BT Sport 2", startTime: getTimeOffset(2880), isPPV: false, isLive: false },
    { id: "ev-7", title: "Monaco Grand Prix — Race Day", competition: "Formula 1", sport: "Motorsport", emoji: "🏎️", channel: "Sky Sports Main Event", startTime: getTimeOffset(4150), isPPV: false, isLive: false },
  ];

  return events.map((e) => ({
    ...e,
    isLive: new Date(e.startTime).getTime() <= Date.now(),
  }));
}
