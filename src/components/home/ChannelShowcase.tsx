"use client";
import Link from "next/link";
import { motion } from "framer-motion";
import { Trophy, Film, Newspaper, Star, Baby, BookOpen, Music, Globe } from "lucide-react";
import { channels, CHANNEL_COUNT_LABEL } from "@/lib/channels";

const categories = [
  { name: "Croatian & Balkan", icon: Globe, color: "#FF4757", description: "HRT, Nova TV, RTL, Doma TV, CMC, N1 & more" },
  { name: "Sports", icon: Trophy, color: "#6C63FF", description: "Live football, cricket, F1, tennis and more" },
  { name: "Movies", icon: Film, color: "#00D4FF", description: "Blockbusters, classics, and indie films" },
  { name: "News", icon: Newspaper, color: "#FF4757", description: "24/7 news from around the globe" },
  { name: "Entertainment", icon: Star, color: "#6C63FF", description: "Soaps, reality TV, and dramas" },
  { name: "Kids", icon: Baby, color: "#00D4FF", description: "Safe, fun content for children" },
  { name: "Documentary", icon: BookOpen, color: "#FF4757", description: "Nature, history, science and more" },
  { name: "Music", icon: Music, color: "#6C63FF", description: "Music videos, live concerts, radio" },
];

export default function ChannelShowcase() {
  return (
    <section className="py-16 px-4 sm:px-6 lg:px-8 bg-brand-card/30">
      <div className="max-w-7xl mx-auto">
        <div className="text-center mb-10">
          <motion.h2
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            className="text-2xl sm:text-3xl font-bold text-white mb-3"
          >
            Explore Our{" "}
            <span className="bg-gradient-to-r from-brand-primary to-brand-secondary bg-clip-text text-transparent">
              Channel Categories
            </span>
          </motion.h2>
          <p className="text-brand-muted">{CHANNEL_COUNT_LABEL} channels across every genre imaginable</p>
        </div>

        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4 mb-6">
          {categories.map((cat, i) => {
            const count = channels.filter((c) => c.category === cat.name).length;
            return (
              <motion.div
                key={cat.name}
                initial={{ opacity: 0, scale: 0.95 }}
                whileInView={{ opacity: 1, scale: 1 }}
                viewport={{ once: true }}
                transition={{ delay: i * 0.05 }}
              >
                <Link href={`/channels?category=${encodeURIComponent(cat.name)}`}>
                  <div
                    className="cyber-panel rounded-2xl p-5 transition-all duration-300 group cursor-pointer h-full hover:-translate-y-1"
                    onMouseEnter={(e) => {
                      e.currentTarget.style.borderColor = `${cat.color}80`;
                      e.currentTarget.style.boxShadow = `0 10px 30px -10px ${cat.color}40`;
                    }}
                    onMouseLeave={(e) => {
                      e.currentTarget.style.borderColor = "";
                      e.currentTarget.style.boxShadow = "";
                    }}
                  >
                    <div
                      className="icon-glow w-12 h-12 rounded-xl flex items-center justify-center mb-3 group-hover:scale-110 transition-transform"
                      style={{ backgroundColor: `${cat.color}1a` }}
                    >
                      <cat.icon className="w-6 h-6" style={{ color: cat.color }} />
                    </div>
                    <h3 className="text-white font-semibold text-sm mb-1">{cat.name}</h3>
                    <p className="text-brand-muted text-xs mb-2 leading-relaxed">{cat.description}</p>
                    <span className="text-xs font-bold" style={{ color: cat.color }}>
                      {count > 0 ? `${count} channels` : "View channels"}
                    </span>
                  </div>
                </Link>
              </motion.div>
            );
          })}
          {/* View All Card */}
          <motion.div
            initial={{ opacity: 0, scale: 0.95 }}
            whileInView={{ opacity: 1, scale: 1 }}
            viewport={{ once: true }}
            transition={{ delay: categories.length * 0.05 }}
          >
            <Link href="/channels">
              <div className="bg-gradient-to-br from-brand-primary/20 to-brand-secondary/10 border border-brand-primary/30 rounded-xl p-5 hover:border-brand-primary/60 hover:shadow-lg hover:shadow-brand-primary/20 transition-all duration-300 group cursor-pointer h-full flex flex-col items-center justify-center text-center hover:-translate-y-1">
                <div className="w-12 h-12 rounded-xl bg-brand-primary/20 flex items-center justify-center mb-3 group-hover:scale-110 transition-transform">
                  <Star className="w-6 h-6 text-brand-primary" />
                </div>
                <h3 className="text-white font-semibold text-sm mb-1">View All</h3>
                <p className="text-brand-muted text-xs">Browse {CHANNEL_COUNT_LABEL} channels</p>
              </div>
            </Link>
          </motion.div>
        </div>
      </div>
    </section>
  );
}
