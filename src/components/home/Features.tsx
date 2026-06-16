"use client";
import { motion } from "framer-motion";
import { Tv, Zap, Clock, Monitor, CalendarDays, Film } from "lucide-react";

const features = [
  {
    icon: Tv,
    title: "10,000+ Live Channels",
    description: "Access thousands of live channels from around the world including sports, movies, news, entertainment, kids, and more.",
    color: "brand-primary",
  },
  {
    icon: Zap,
    title: "4K Ultra HD Quality",
    description: "Enjoy crystal-clear 4K UHD, Full HD, and HD streams. Experience your content the way it was meant to be seen.",
    color: "brand-secondary",
  },
  {
    icon: Clock,
    title: "7-30 Day Catch-Up",
    description: "Never miss a show again. With our catch-up TV feature, rewind and watch programs from the last 7 to 30 days.",
    color: "brand-accent",
  },
  {
    icon: Monitor,
    title: "Multi-Device Support",
    description: "Watch on Smart TVs, Firestick, MAG boxes, phones, tablets, and computers. Up to 3 simultaneous connections.",
    color: "brand-primary",
  },
  {
    icon: CalendarDays,
    title: "Electronic Program Guide",
    description: "Intuitive EPG with detailed program listings, schedules, and descriptions. Plan your viewing with ease.",
    color: "brand-secondary",
  },
  {
    icon: Film,
    title: "Massive VOD Library",
    description: "Thousands of movies and TV shows on demand. New content added daily. Watch what you want, when you want.",
    color: "brand-accent",
  },
];

const containerVariants = {
  hidden: {},
  visible: {
    transition: {
      staggerChildren: 0.1,
    },
  },
};

const itemVariants = {
  hidden: { opacity: 0, y: 30 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.5 } },
};

export default function Features() {
  return (
    <section className="py-20 px-4 sm:px-6 lg:px-8">
      <div className="max-w-7xl mx-auto">
        <div className="text-center mb-12">
          <motion.h2
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            className="text-3xl sm:text-4xl font-bold text-white mb-4"
          >
            Why Choose{" "}
            <span className="bg-gradient-to-r from-brand-primary to-brand-secondary bg-clip-text text-transparent">
              Enktel IPTV?
            </span>
          </motion.h2>
          <motion.p
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={{ delay: 0.1 }}
            className="text-brand-muted text-lg max-w-2xl mx-auto"
          >
            Everything you need for the ultimate streaming experience, all in one place.
          </motion.p>
        </div>

        <motion.div
          variants={containerVariants}
          initial="hidden"
          whileInView="visible"
          viewport={{ once: true }}
          className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6"
        >
          {features.map((feature) => (
            <motion.div
              key={feature.title}
              variants={itemVariants}
              className="bg-brand-card border border-brand-border rounded-xl p-6 hover:border-brand-primary/40 hover:shadow-lg hover:shadow-brand-primary/10 transition-all duration-300 group"
            >
              <div className={`w-12 h-12 rounded-xl bg-${feature.color}/10 flex items-center justify-center mb-4 group-hover:scale-110 transition-transform`}>
                <feature.icon className={`w-6 h-6 text-${feature.color}`} />
              </div>
              <h3 className="text-white font-semibold text-lg mb-2">{feature.title}</h3>
              <p className="text-brand-muted text-sm leading-relaxed">{feature.description}</p>
            </motion.div>
          ))}
        </motion.div>
      </div>
    </section>
  );
}
