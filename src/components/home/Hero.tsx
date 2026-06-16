"use client";
import Link from "next/link";
import { motion } from "framer-motion";
import { Play, ChevronRight, Tv, Zap, Shield } from "lucide-react";
import Button from "@/components/ui/Button";

export default function Hero() {
  return (
    <section className="relative min-h-screen flex items-center justify-center overflow-hidden bg-brand-bg">
      {/* Background glows */}
      <div className="absolute inset-0 overflow-hidden pointer-events-none">
        <div className="absolute top-1/4 left-1/4 w-96 h-96 bg-brand-primary/20 rounded-full blur-3xl animate-pulse-glow" />
        <div className="absolute bottom-1/4 right-1/4 w-80 h-80 bg-brand-secondary/15 rounded-full blur-3xl" style={{ animationDelay: "1.5s" }} />
        <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-64 h-64 bg-brand-accent/10 rounded-full blur-3xl" />
        {/* Grid overlay */}
        <div
          className="absolute inset-0 opacity-5"
          style={{
            backgroundImage: "linear-gradient(rgba(108,99,255,0.3) 1px, transparent 1px), linear-gradient(90deg, rgba(108,99,255,0.3) 1px, transparent 1px)",
            backgroundSize: "60px 60px",
          }}
        />
      </div>

      <div className="relative z-10 max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 pt-24 pb-16">
        <div className="text-center">
          {/* Badge */}
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5 }}
            className="inline-flex items-center gap-2 bg-brand-primary/10 border border-brand-primary/30 rounded-full px-4 py-2 mb-8"
          >
            <span className="w-2 h-2 bg-brand-accent rounded-full animate-pulse" />
            <span className="text-brand-primary text-sm font-medium">Now Streaming in 4K Ultra HD</span>
          </motion.div>

          {/* Heading */}
          <motion.h1
            initial={{ opacity: 0, y: 30 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, delay: 0.1 }}
            className="text-5xl sm:text-6xl lg:text-7xl font-bold mb-6 leading-tight"
          >
            <span className="text-white">Stream</span>{" "}
            <span className="bg-gradient-to-r from-brand-primary via-brand-secondary to-brand-primary bg-clip-text text-transparent">
              Beyond
            </span>
            <br />
            <span className="text-white">Limits</span>
          </motion.h1>

          {/* Sub-heading */}
          <motion.p
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, delay: 0.2 }}
            className="text-brand-muted text-lg sm:text-xl max-w-2xl mx-auto mb-10 leading-relaxed"
          >
            Experience premium IPTV with over 10,000+ live channels, 4K Ultra HD quality,
            and a massive VOD library. Enktel IPTV — your ultimate streaming destination.
          </motion.p>

          {/* CTAs */}
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, delay: 0.3 }}
            className="flex flex-col sm:flex-row items-center justify-center gap-4 mb-16"
          >
            <Link href="/pricing">
              <Button size="lg" className="group">
                <Play className="w-5 h-5 mr-2 fill-current" />
                Start Streaming
                <ChevronRight className="w-4 h-4 ml-2 group-hover:translate-x-1 transition-transform" />
              </Button>
            </Link>
            <Link href="/channels">
              <Button variant="outline" size="lg">
                <Tv className="w-5 h-5 mr-2" />
                View Channels
              </Button>
            </Link>
          </motion.div>

          {/* Floating stat cards */}
          <motion.div
            initial={{ opacity: 0, y: 40 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8, delay: 0.4 }}
            className="grid grid-cols-1 sm:grid-cols-3 gap-4 max-w-3xl mx-auto"
          >
            {[
              { icon: Tv, label: "Live Channels", value: "10,000+", color: "brand-primary" },
              { icon: Zap, label: "Stream Quality", value: "4K UHD", color: "brand-secondary" },
              { icon: Shield, label: "Uptime", value: "99.9%", color: "brand-accent" },
            ].map(({ icon: Icon, label, value, color }) => (
              <div
                key={label}
                className="bg-white/5 backdrop-blur-sm border border-brand-border rounded-xl p-4 flex items-center gap-3"
              >
                <div className={`w-10 h-10 rounded-lg bg-${color}/20 flex items-center justify-center shrink-0`}>
                  <Icon className={`w-5 h-5 text-${color}`} />
                </div>
                <div className="text-left">
                  <div className="text-white font-bold text-lg leading-none">{value}</div>
                  <div className="text-brand-muted text-xs mt-0.5">{label}</div>
                </div>
              </div>
            ))}
          </motion.div>
        </div>
      </div>

      {/* Bottom fade */}
      <div className="absolute bottom-0 left-0 right-0 h-32 bg-gradient-to-t from-brand-bg to-transparent pointer-events-none" />
    </section>
  );
}
