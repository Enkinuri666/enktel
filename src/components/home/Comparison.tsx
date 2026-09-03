"use client";
import { motion } from "framer-motion";
import { Trophy, Film, Tv } from "lucide-react";

/**
 * Enktel against what an Australian household is otherwise paying for.
 *
 * The argument the whole page rests on is subscription fatigue: the content is
 * not missing, it is scattered across five services that each want a monthly
 * fee. So the comparison is deliberately laid out as "what you juggle now"
 * beside "what this replaces", rather than as a feature checklist.
 */

interface Row {
  label: string;
  them: string;
  us: string;
}

const TABLES: { icon: typeof Trophy; title: string; blurb: string; themHead: string; rows: Row[]; hook: string }[] = [
  {
    icon: Trophy,
    title: "Live Sports & PPV",
    blurb:
      "Australian sports broadcasting is fragmented, forcing fans to buy multiple subscriptions and pay huge fees for single events.",
    themHead: "What you pay for now",
    rows: [
      {
        label: "Aussie sports — AFL, NRL, Supercars, cricket",
        them: "Kayo: Australian and Fox feeds only, often delayed on basic tiers.",
        us: "Every Fox League, Fox Footy and local channel, live in HD/4K.",
      },
      {
        label: "Global football — EPL, Champions League",
        them: "Optus Sport + Stan Sport: two separate apps, $40+ combined a month.",
        us: "All Optus channels, plus UK Sky Sports, TNT Sports and beIN Sports.",
      },
      {
        label: "Pay-per-view — UFC, boxing, WWE",
        them: "Main Event / Kayo PPV: $59.95 per fight on top of your monthly sub.",
        us: "Every global PPV event included at no extra cost.",
      },
      {
        label: "US sports — NBA, NFL, NHL",
        them: "ESPN via Kayo: a limited number of games per week.",
        us: "US local networks, NBA League Pass and NFL Sunday Ticket feeds.",
      },
    ],
    hook:
      "Why pay $60 to watch one UFC fight, when every PPV event, the Premier League and the footy all sit in one place?",
  },
  {
    icon: Film,
    title: "Video on Demand",
    blurb:
      "The biggest complaint about streaming today is subscription fatigue — paying Netflix, Binge, Disney+ and Amazon just to keep up with different shows.",
    themHead: "Standard streaming apps",
    rows: [
      {
        label: "Movie library",
        them: "Fragmented. Search Netflix, then Amazon, then Stan to find one film.",
        us: "One massive unified library. Tens of thousands of films, one search box.",
      },
      {
        label: "New releases",
        them: "Wait months after cinema, or pay $25 to rent on Apple TV.",
        us: "Cinema releases and premium digital rentals added straight to VOD.",
      },
      {
        label: "TV series & boxsets",
        them: "HBO locked to Binge, Stranger Things to Netflix, Marvel to Disney+.",
        us: "Every major hit series from Netflix, HBO, Apple TV+ and Hulu, in full boxsets.",
      },
      {
        label: "Stand-up & documentaries",
        them: "Limited to whatever that one platform happens to produce.",
        us: "Dedicated categories for comedy specials, true crime and global docs.",
      },
    ],
    hook:
      "No switching inputs and loading four apps to find a film. Enktel pulls the libraries into one dashboard.",
  },
  {
    icon: Tv,
    title: "Live TV",
    blurb: "Where the traditional set-top box stops making sense.",
    themHead: "Foxtel Platinum",
    rows: [
      { label: "Channel count", them: "Around 140 channels.", us: "10,000+ channels." },
      {
        label: "International content",
        them: "Very limited — a few news channels.",
        us: "Dedicated folders for the USA, UK, Canada, Europe and Asia. Built for expats.",
      },
      {
        label: "24/7 binge channels",
        them: "None.",
        us: "Hundreds of channels playing back-to-back episodes of classic shows.",
      },
      {
        label: "Hardware required",
        them: "Set-top box, satellite dish, installation and technician fees.",
        us: "An app on the Smart TV, Firestick or Android box you already own.",
      },
    ],
    hook: "No dish, no technician, no box. Just the screen you already have.",
  },
];

export default function Comparison() {
  return (
    <section className="py-20 px-4 sm:px-6 lg:px-8">
      <div className="max-w-7xl mx-auto">
        <div className="text-center mb-14">
          <p className="text-brand-primary text-sm font-bold uppercase tracking-widest mb-3">
            One Platform, Not Five Subscriptions
          </p>
          <h2 className="text-3xl sm:text-5xl font-black text-white mb-4">
            How Many Streaming Apps Are You{" "}
            <span className="bg-gradient-to-r from-brand-primary to-brand-secondary bg-clip-text text-transparent">
              Paying For?
            </span>
          </h2>
          <p className="text-brand-muted text-lg max-w-2xl mx-auto">
            Enktel replaces the lot. Here is exactly what that means, category by
            category.
          </p>
        </div>

        <div className="space-y-10">
          {TABLES.map((table, ti) => (
            <motion.div
              key={table.title}
              initial={{ opacity: 0, y: 24 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
              transition={{ delay: ti * 0.05 }}
              className="bg-brand-card border border-brand-border rounded-2xl p-6 sm:p-8"
            >
              <div className="flex items-start gap-3 mb-5">
                <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-brand-primary to-brand-secondary p-0.5 shrink-0">
                  <div className="w-full h-full bg-brand-card rounded-[10px] flex items-center justify-center">
                    <table.icon className="w-5 h-5 text-white" />
                  </div>
                </div>
                <div>
                  <h3 className="text-white font-bold text-xl">{table.title}</h3>
                  <p className="text-brand-muted text-sm mt-1 max-w-3xl">{table.blurb}</p>
                </div>
              </div>

              {/* Scrolls inside itself: three columns of prose do not fit a phone,
                  and the page body must never scroll sideways. */}
              <div className="overflow-x-auto -mx-2 px-2">
                <table className="w-full min-w-[640px] text-sm border-collapse">
                  <thead>
                    <tr className="text-left">
                      <th className="py-2 pr-4 text-brand-muted font-semibold w-1/4">Content</th>
                      <th className="py-2 pr-4 text-brand-muted font-semibold w-[37.5%]">
                        {table.themHead}
                      </th>
                      <th className="py-2 text-brand-primary font-black w-[37.5%]">Enktel</th>
                    </tr>
                  </thead>
                  <tbody>
                    {table.rows.map((row) => (
                      <tr key={row.label} className="border-t border-brand-border align-top">
                        <td className="py-3 pr-4 text-white font-semibold">{row.label}</td>
                        <td className="py-3 pr-4 text-brand-muted">{row.them}</td>
                        <td className="py-3 text-white">{row.us}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              <p className="mt-5 text-brand-secondary text-sm font-semibold border-l-2 border-brand-secondary/50 pl-3">
                {table.hook}
              </p>
            </motion.div>
          ))}
        </div>
      </div>
    </section>
  );
}
