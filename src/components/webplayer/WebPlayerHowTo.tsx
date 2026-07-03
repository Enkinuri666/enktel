"use client";
import { motion } from "framer-motion";
import { Globe2, KeyRound, ListVideo, MousePointerClick } from "lucide-react";

const steps = [
  {
    icon: Globe2,
    title: "Go to watch.enktel.tv",
    description: "Open any modern browser — on your laptop, desktop, or tablet — and visit watch.enktel.tv. No download or install needed.",
  },
  {
    icon: KeyRound,
    title: "Log in with your Enktel details",
    description: "Enter the same username and password from your Enktel IPTV subscription — the one you already use on your Smart TV, Firestick, or MAG box.",
  },
  {
    icon: ListVideo,
    title: "Browse the Live TV Guide",
    description: "Check what's on now and next, filter channels by category, or search for a specific channel using the built-in guide.",
  },
  {
    icon: MousePointerClick,
    title: "Click to start watching",
    description: "Select any channel or guide entry and it starts streaming immediately — right there in your browser, at no extra cost.",
  },
];

export default function WebPlayerHowTo() {
  return (
    <section className="py-16 px-4 sm:px-6 lg:px-8">
      <div className="max-w-5xl mx-auto">
        <div className="text-center mb-14">
          <motion.p initial={{ opacity: 0 }} whileInView={{ opacity: 1 }} viewport={{ once: true }} className="text-brand-secondary text-sm font-bold uppercase tracking-widest mb-3">
            Getting Started
          </motion.p>
          <motion.h2 initial={{ opacity: 0, y: 20 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }} className="text-3xl sm:text-5xl font-black text-white mb-4">
            How to Use the{" "}
            <span className="bg-gradient-to-r from-brand-secondary to-brand-primary bg-clip-text text-transparent">Web Player</span>
          </motion.h2>
          <motion.p initial={{ opacity: 0, y: 20 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }} transition={{ delay: 0.1 }} className="text-brand-muted text-lg max-w-2xl mx-auto">
            You&apos;re already set up — it takes less than a minute to get started.
          </motion.p>
        </div>

        <div className="relative">
          <div className="hidden sm:block absolute top-7 left-0 right-0 h-px bg-brand-border" />
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-8 sm:gap-6">
            {steps.map((step, i) => (
              <motion.div
                key={step.title}
                initial={{ opacity: 0, y: 30 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true }}
                transition={{ delay: i * 0.1 }}
                className="relative text-center sm:text-left"
              >
                <div className="relative z-10 w-14 h-14 mx-auto sm:mx-0 rounded-2xl bg-brand-card border-2 border-brand-secondary/40 flex items-center justify-center mb-4">
                  <step.icon className="w-6 h-6 text-brand-secondary" />
                  <span className="absolute -top-2 -right-2 w-6 h-6 rounded-full bg-brand-secondary text-brand-bg text-xs font-black flex items-center justify-center">
                    {i + 1}
                  </span>
                </div>
                <h3 className="text-white font-bold text-base mb-2">{step.title}</h3>
                <p className="text-brand-muted text-sm leading-relaxed">{step.description}</p>
              </motion.div>
            ))}
          </div>
        </div>

        <motion.div
          initial={{ opacity: 0, y: 20 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          className="mt-12 bg-brand-card border border-brand-border rounded-2xl p-6 sm:p-8 text-center"
        >
          <p className="text-white font-semibold mb-1">Don&apos;t have your login details handy?</p>
          <p className="text-brand-muted text-sm">
            Find your username, password, and setup info anytime in{" "}
            <a href="/dashboard" className="text-brand-secondary hover:underline font-medium">
              your Enktel Dashboard
            </a>
            , or reach out to 24/7 support via the WhatsApp button on this site.
          </p>
        </motion.div>
      </div>
    </section>
  );
}
