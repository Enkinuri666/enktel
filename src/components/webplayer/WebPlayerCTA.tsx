"use client";
import { motion } from "framer-motion";
import Image from "next/image";
import { MonitorPlay, ArrowRight } from "lucide-react";
import { WEB_PLAYER_URL } from "@/lib/webplayer";

export default function WebPlayerCTA() {
  return (
    <section className="py-16 px-4 sm:px-6 lg:px-8">
      <div className="max-w-7xl mx-auto">
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          className="relative overflow-hidden rounded-3xl border border-brand-secondary/30 bg-gradient-to-br from-brand-secondary via-[#00b8e0] to-brand-primary p-8 sm:p-12 text-center"
        >
          <Image
            src="/logo-icon.png"
            alt=""
            width={220}
            height={220}
            className="absolute -right-6 -bottom-10 w-44 sm:w-56 opacity-15 pointer-events-none select-none"
          />
          <div className="absolute -top-16 -left-16 w-72 h-72 bg-white/10 rounded-full blur-3xl" />

          <div className="relative z-10 max-w-2xl mx-auto">
            <h2 className="text-3xl sm:text-4xl font-black text-white mb-3">
              Already an Enktel member?
            </h2>
            <p className="text-white/85 text-lg mb-8">
              Your web player access is already active — just log in with your existing details and start watching.
            </p>
            <a href={WEB_PLAYER_URL} target="_blank" rel="noopener noreferrer">
              <button className="group inline-flex items-center gap-3 bg-white text-brand-primary font-bold px-8 py-4 rounded-xl transition-all duration-300 hover:-translate-y-1 hover:shadow-2xl hover:shadow-black/20">
                <MonitorPlay className="w-4 h-4" />
                Go to watch.enktel.tv
                <ArrowRight className="w-4 h-4 group-hover:translate-x-1 transition-transform" />
              </button>
            </a>
          </div>
        </motion.div>
      </div>
    </section>
  );
}
