"use client";
import { motion } from "framer-motion";
import { MonitorPlay, KeyRound, CalendarDays, Gauge, Search, Heart, ShieldCheck, Gift } from "lucide-react";

const features = [
  { icon: Gift, title: "Included Free", description: "No extra charge, no add-on plan. Full web player access comes with every active Enktel IPTV subscription.", gradient: "from-brand-secondary to-teal-500" },
  { icon: KeyRound, title: "Use Your Existing Login", description: "Sign in with the same username and password you already use on Smart TV, Firestick or MAG box — no new account needed.", gradient: "from-brand-primary to-blue-600" },
  { icon: MonitorPlay, title: "Instant Browser Streaming", description: "Watch live channels straight in Chrome, Safari, Edge or Firefox. No apps, downloads, or extra hardware required.", gradient: "from-[#CE2C1A] to-brand-accent" },
  { icon: CalendarDays, title: "Full Live TV Guide", description: "Browse the complete Electronic Program Guide with what's on now and next for every channel, right alongside the player.", gradient: "from-brand-primary to-brand-secondary" },
  { icon: Search, title: "Search & Browse Channels", description: "Filter by category — Croatian & Balkan, Sports, Movies, UK & International — and jump straight to any channel.", gradient: "from-purple-600 to-brand-primary" },
  { icon: Heart, title: "Favourites", description: "Pin the channels you watch most so they're always one click away.", gradient: "from-brand-secondary to-blue-600" },
  { icon: Gauge, title: "Fast Channel Switching", description: "Move between channels instantly with a lightweight, responsive player built for speed.", gradient: "from-brand-accent to-orange-500" },
  { icon: ShieldCheck, title: "Secure & Tied to Your Plan", description: "Access is linked directly to your Enktel subscription, so it's always in sync with your account status.", gradient: "from-green-500 to-teal-600" },
];

export default function WebPlayerFeatures() {
  return (
    <section className="py-16 px-4 sm:px-6 lg:px-8">
      <div className="max-w-7xl mx-auto">
        <div className="text-center mb-14">
          <motion.p initial={{ opacity: 0 }} whileInView={{ opacity: 1 }} viewport={{ once: true }} className="text-brand-secondary text-sm font-bold uppercase tracking-widest mb-3">
            Everything Included
          </motion.p>
          <motion.h2 initial={{ opacity: 0, y: 20 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }} className="text-3xl sm:text-5xl font-black text-white mb-4">
            Web Player{" "}
            <span className="bg-gradient-to-r from-brand-secondary to-brand-primary bg-clip-text text-transparent">Features</span>
          </motion.h2>
          <motion.p initial={{ opacity: 0, y: 20 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }} transition={{ delay: 0.1 }} className="text-brand-muted text-lg max-w-2xl mx-auto">
            Everything you get at watch.enktel.tv — at no extra cost, on top of your existing subscription.
          </motion.p>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5">
          {features.map((feature, i) => (
            <motion.div
              key={feature.title}
              initial={{ opacity: 0, y: 30 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
              transition={{ delay: i * 0.06 }}
              className="group relative bg-brand-card border border-brand-border rounded-2xl p-6 hover:border-brand-secondary/50 transition-all duration-300 hover:shadow-xl hover:shadow-brand-secondary/10 hover:-translate-y-1 overflow-hidden"
            >
              <div className={`absolute -top-8 -right-8 w-24 h-24 bg-gradient-to-br ${feature.gradient} rounded-full blur-2xl opacity-0 group-hover:opacity-20 transition-opacity duration-500`} />

              <div className={`w-12 h-12 rounded-xl bg-gradient-to-br ${feature.gradient} p-0.5 mb-4`}>
                <div className="w-full h-full bg-brand-card rounded-[10px] flex items-center justify-center">
                  <feature.icon className="w-6 h-6 text-white" />
                </div>
              </div>
              <h3 className="text-white font-bold text-base mb-2">{feature.title}</h3>
              <p className="text-brand-muted text-sm leading-relaxed">{feature.description}</p>
            </motion.div>
          ))}
        </div>
      </div>
    </section>
  );
}
