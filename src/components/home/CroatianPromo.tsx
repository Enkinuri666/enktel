"use client";
import Link from "next/link";
import { motion } from "framer-motion";
import { ArrowRight, Tv } from "lucide-react";

const croatianChannels = [
  { name: "HRT 1", type: "General" },
  { name: "HRT 2", type: "General" },
  { name: "HRT 3", type: "Culture" },
  { name: "HRT 4", type: "Documentary" },
  { name: "Nova TV", type: "Entertainment" },
  { name: "RTL HR", type: "Entertainment" },
  { name: "RTL 2", type: "Entertainment" },
  { name: "Doma TV", type: "Lifestyle" },
  { name: "CMC TV", type: "Music" },
  { name: "N1 Info", type: "News" },
  { name: "24sata TV", type: "News" },
  { name: "IN TV", type: "Regional" },
];

const balkanChannels = [
  { name: "RTS 1", country: "🇷🇸" },
  { name: "FTV", country: "🇧🇦" },
  { name: "Hayat TV", country: "🇧🇦" },
  { name: "POP TV", country: "🇸🇮" },
  { name: "Kanal 5", country: "🇲🇰" },
  { name: "RTCG", country: "🇲🇪" },
];

export default function CroatianPromo() {
  return (
    <section className="relative py-20 px-4 sm:px-6 lg:px-8 overflow-hidden">
      {/* Background */}
      <div className="absolute inset-0">
        <div className="absolute inset-0 bg-gradient-to-br from-[#CE2C1A]/5 via-brand-bg to-brand-bg" />
        <div className="absolute top-0 left-0 w-full h-px bg-gradient-to-r from-transparent via-[#CE2C1A]/30 to-transparent" />
        <div className="absolute bottom-0 left-0 w-full h-px bg-gradient-to-r from-transparent via-brand-border to-transparent" />
      </div>

      <div className="relative z-10 max-w-7xl mx-auto">
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-12 items-center">

          {/* LEFT: Channels visual */}
          <motion.div
            initial={{ opacity: 0, x: -30 }}
            whileInView={{ opacity: 1, x: 0 }}
            viewport={{ once: true }}
            className="order-2 lg:order-1"
          >
            {/* Croatian channels card */}
            <div className="bg-brand-card border border-brand-border rounded-2xl p-6 mb-4 relative overflow-hidden">
              <div className="absolute top-0 right-0 w-32 h-32 bg-[#CE2C1A]/10 rounded-full blur-2xl" />
              <div className="flex items-center gap-3 mb-5">
                <span className="text-2xl">🇭🇷</span>
                <div>
                  <h3 className="text-white font-bold">Hrvatska Televizija</h3>
                  <p className="text-brand-muted text-xs">12 Croatian channels + more</p>
                </div>
                <span className="ml-auto bg-[#CE2C1A]/20 text-[#CE2C1A] border border-[#CE2C1A]/30 text-xs font-bold px-3 py-1 rounded-full">HRVATSKA</span>
              </div>
              <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
                {croatianChannels.map((ch) => (
                  <div key={ch.name} className="bg-brand-bg border border-brand-border rounded-lg px-3 py-2">
                    <div className="text-white text-xs font-semibold">{ch.name}</div>
                    <div className="text-brand-muted text-xs">{ch.type}</div>
                  </div>
                ))}
              </div>
            </div>

            {/* Balkan channels strip */}
            <div className="bg-brand-card border border-brand-border rounded-xl p-4">
              <p className="text-brand-muted text-xs font-semibold uppercase tracking-wider mb-3">Also Included: Balkan Region</p>
              <div className="flex flex-wrap gap-2">
                {balkanChannels.map((ch) => (
                  <div key={ch.name} className="flex items-center gap-1.5 bg-brand-bg border border-brand-border rounded-lg px-3 py-1.5">
                    <span className="text-sm">{ch.country}</span>
                    <span className="text-white text-xs font-medium">{ch.name}</span>
                  </div>
                ))}
                <div className="flex items-center gap-1.5 bg-brand-primary/10 border border-brand-primary/30 rounded-lg px-3 py-1.5">
                  <Tv className="w-3.5 h-3.5 text-brand-primary" />
                  <span className="text-brand-primary text-xs font-medium">50+ more</span>
                </div>
              </div>
            </div>
          </motion.div>

          {/* RIGHT: Text content */}
          <motion.div
            initial={{ opacity: 0, x: 30 }}
            whileInView={{ opacity: 1, x: 0 }}
            viewport={{ once: true }}
            className="order-1 lg:order-2"
          >
            <div className="inline-flex items-center gap-2 bg-[#CE2C1A]/10 border border-[#CE2C1A]/30 rounded-full px-4 py-2 mb-6">
              <span className="text-xl">🇭🇷</span>
              <span className="text-[#CE2C1A] text-sm font-bold">HRVATSKA / BALKAN</span>
            </div>

            <h2 className="text-3xl sm:text-5xl font-black text-white mb-4 leading-tight">
              Gledajte Domaće TV<br />
              <span className="bg-gradient-to-r from-[#CE2C1A] to-brand-accent bg-clip-text text-transparent">
                Gdje God Bili.
              </span>
            </h2>

            <p className="text-white/70 text-lg mb-3">
              <em>&ldquo;Watch your home TV wherever you are.&rdquo;</em>
            </p>

            <p className="text-brand-muted leading-relaxed mb-6">
              Living abroad? Never miss another episode of your favourite Croatian show. Stream all major Croatian and Balkan channels live — news, sport, entertainment and more — from anywhere in the world with Enktel IPTV.
            </p>

            <ul className="space-y-3 mb-8">
              {[
                "All HRT channels (HRT 1, 2, 3, 4)",
                "Nova TV, RTL Hrvatska, Doma TV & CMC TV",
                "Balkan region: Serbia, Bosnia, Slovenia & more",
                "Full catch-up TV — never miss a show",
                "Works on every device, anywhere in the world",
              ].map((item) => (
                <li key={item} className="flex items-start gap-3 text-brand-muted text-sm">
                  <span className="text-[#CE2C1A] mt-0.5 shrink-0">✦</span>
                  {item}
                </li>
              ))}
            </ul>

            <Link href="/epg">
              <button className="group flex items-center gap-3 bg-[#CE2C1A] hover:bg-red-700 text-white font-bold px-8 py-4 rounded-xl transition-all duration-300 hover:shadow-xl hover:shadow-[#CE2C1A]/30 hover:-translate-y-0.5">
                Browse Croatian Channels
                <ArrowRight className="w-4 h-4 group-hover:translate-x-1 transition-transform" />
              </button>
            </Link>
          </motion.div>

        </div>
      </div>
    </section>
  );
}
