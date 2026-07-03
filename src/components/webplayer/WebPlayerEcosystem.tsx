"use client";
import Link from "next/link";
import Image from "next/image";
import { motion } from "framer-motion";
import { Tv, Laptop, ArrowRight } from "lucide-react";
import { WEB_PLAYER_URL } from "@/lib/webplayer";

export default function WebPlayerEcosystem() {
  return (
    <section className="relative py-16 px-4 sm:px-6 lg:px-8 overflow-hidden">
      <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[70%] h-[70%] bg-brand-primary/10 rounded-full blur-3xl pointer-events-none" />
      <div className="absolute top-0 right-0 w-96 h-96 bg-brand-secondary/10 rounded-full blur-3xl pointer-events-none" />

      <div className="relative z-10 max-w-6xl mx-auto">
        <div className="text-center mb-10">
          <motion.p initial={{ opacity: 0 }} whileInView={{ opacity: 1 }} viewport={{ once: true }} className="text-brand-primary text-sm font-bold uppercase tracking-widest mb-3">
            One Account, Every Screen
          </motion.p>
          <motion.h2 initial={{ opacity: 0, y: 20 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }} className="text-3xl sm:text-5xl font-black text-white mb-4">
            The Enktel{" "}
            <span className="bg-gradient-to-r from-brand-secondary via-brand-primary to-[#b46bff] bg-clip-text text-transparent">
              Ecosystem
            </span>
          </motion.h2>
          <motion.p initial={{ opacity: 0, y: 20 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }} transition={{ delay: 0.1 }} className="text-brand-muted text-lg max-w-2xl mx-auto">
            Your subscription isn&apos;t tied to one screen. Watch on your Smart TV or streaming box through{" "}
            <span className="text-white font-semibold">enktel.tv</span>, or open the same channels and guide
            in any browser at <span className="text-white font-semibold">watch.enktel.tv</span> — same login, every device.
          </motion.p>
        </div>

        <motion.div
          initial={{ opacity: 0, y: 30 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ delay: 0.15 }}
          className="relative rounded-3xl overflow-hidden border border-white/10 shadow-2xl shadow-black/50"
        >
          <div className="absolute -inset-px rounded-3xl bg-gradient-to-r from-brand-secondary/30 via-transparent to-brand-primary/30 pointer-events-none" />
          <Image
            src="/images/ecosystem-devices.jpg"
            alt="EnkTel IPTV on a Smart TV via enktel.tv, alongside the watch.enktel.tv web player running on a laptop, tablet, and phone"
            width={2848}
            height={1600}
            className="w-full h-auto relative"
            sizes="(max-width: 1024px) 100vw, 1152px"
            priority={false}
          />
        </motion.div>

        <motion.div
          initial={{ opacity: 0, y: 20 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ delay: 0.25 }}
          className="mt-8 grid grid-cols-1 sm:grid-cols-2 gap-4"
        >
          <div className="flex items-center gap-4 bg-brand-card border border-brand-border rounded-2xl p-5">
            <div className="w-11 h-11 rounded-xl bg-brand-secondary/15 border border-brand-secondary/30 flex items-center justify-center shrink-0">
              <Tv className="w-5 h-5 text-brand-secondary" />
            </div>
            <div className="flex-1">
              <p className="text-white font-bold text-sm">enktel.tv</p>
              <p className="text-brand-muted text-xs">Smart TV, Firestick &amp; MAG apps</p>
            </div>
          </div>
          <a
            href={WEB_PLAYER_URL}
            target="_blank"
            rel="noopener noreferrer"
            className="group flex items-center gap-4 bg-brand-card border border-brand-border rounded-2xl p-5 hover:border-brand-primary/50 transition-colors"
          >
            <div className="w-11 h-11 rounded-xl bg-brand-primary/15 border border-brand-primary/30 flex items-center justify-center shrink-0">
              <Laptop className="w-5 h-5 text-brand-primary" />
            </div>
            <div className="flex-1">
              <p className="text-white font-bold text-sm flex items-center gap-1.5">
                watch.enktel.tv
                <ArrowRight className="w-3.5 h-3.5 group-hover:translate-x-1 transition-transform" />
              </p>
              <p className="text-brand-muted text-xs">Laptop, tablet &amp; phone browsers</p>
            </div>
          </a>
        </motion.div>

        <div className="text-center mt-8">
          <Link href="/pricing" className="text-brand-secondary hover:underline text-sm font-medium">
            Not a member yet? See plans at enktel.tv →
          </Link>
        </div>
      </div>
    </section>
  );
}
