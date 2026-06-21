"use client";
import { motion } from "framer-motion";
import { Tv, Zap, Clock, Monitor, CalendarDays, Film, Globe, Headphones } from "lucide-react";
import { CHANNEL_COUNT_LABEL } from "@/lib/channels";

const features = [
  { icon: Tv, title: `${CHANNEL_COUNT_LABEL} Live Channels`, description: "Sports, movies, news, kids, music and entertainment — all in one place, streaming live 24/7.", gradient: "from-brand-primary to-blue-600" },
  { icon: Zap, title: "4K Ultra HD Quality", description: "Crystal-clear 4K UHD streams. Watch your content the way it was meant to be seen.", gradient: "from-brand-secondary to-teal-500" },
  { icon: Globe, title: "50+ Countries", description: "Croatian, Balkan, UK, European and worldwide channels. Your home TV, wherever you are.", gradient: "from-[#CE2C1A] to-brand-accent" },
  { icon: Clock, title: "30-Day Catch-Up TV", description: "Missed a show? Rewind and watch up to 30 days of past programming, any time.", gradient: "from-brand-accent to-orange-500" },
  { icon: CalendarDays, title: "Electronic Program Guide", description: "Intuitive EPG with detailed schedules, descriptions, and reminders.", gradient: "from-brand-primary to-brand-secondary" },
  { icon: Film, title: "Massive VOD Library", description: "Thousands of movies and series on demand. New content added daily.", gradient: "from-purple-600 to-brand-primary" },
  { icon: Monitor, title: "All Devices", description: "Smart TV, Firestick, phone, tablet, computer — stream on any screen, anywhere.", gradient: "from-brand-secondary to-blue-600" },
  { icon: Headphones, title: "24/7 Support", description: "Expert support team available around the clock. Setup help, technical issues — we&apos;ve got you.", gradient: "from-green-500 to-teal-600" },
];

export default function Features() {
  return (
    <section className="relative py-20 px-4 sm:px-6 lg:px-8 cyber-grid">
      <div className="max-w-7xl mx-auto relative z-10">
        <div className="text-center mb-16">
          <motion.p initial={{ opacity: 0 }} whileInView={{ opacity: 1 }} viewport={{ once: true }} className="text-brand-primary text-sm font-bold uppercase tracking-widest mb-3">
            Everything You Need
          </motion.p>
          <motion.h2 initial={{ opacity: 0, y: 20 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }} className="text-3xl sm:text-5xl font-black text-white mb-4">
            Why Choose{" "}
            <span className="bg-gradient-to-r from-brand-primary to-brand-secondary bg-clip-text text-transparent">Enktel IPTV?</span>
          </motion.h2>
          <motion.p initial={{ opacity: 0, y: 20 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }} transition={{ delay: 0.1 }} className="text-brand-muted text-lg max-w-2xl mx-auto">
            Premium streaming built for Croatian and worldwide audiences.
          </motion.p>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5">
          {features.map((feature, i) => (
            <motion.div
              key={feature.title}
              initial={{ opacity: 0, y: 30 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
              transition={{ delay: i * 0.08 }}
              className="group relative cyber-panel cyber-panel-hover rounded-2xl p-6 overflow-hidden"
            >
              {/* Gradient corner glow */}
              <div className={`absolute -top-8 -right-8 w-24 h-24 bg-gradient-to-br ${feature.gradient} rounded-full blur-2xl opacity-0 group-hover:opacity-25 transition-opacity duration-500`} />

              <div className={`icon-glow w-12 h-12 rounded-xl bg-gradient-to-br ${feature.gradient} p-0.5 mb-4`}>
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
