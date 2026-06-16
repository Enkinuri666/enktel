import { Tv, Users, Clock, Globe } from "lucide-react";

const stats = [
  { icon: Tv, value: "10,000+", label: "Live Channels", color: "brand-primary" },
  { icon: Globe, value: "50+", label: "Countries Covered", color: "brand-secondary" },
  { icon: Clock, value: "99.9%", label: "Uptime Guarantee", color: "brand-accent" },
  { icon: Users, value: "50,000+", label: "Happy Customers", color: "brand-primary" },
];

export default function StatsBar() {
  return (
    <section className="py-8 px-4 sm:px-6 lg:px-8">
      <div className="max-w-7xl mx-auto">
        <div className="bg-brand-card/60 backdrop-blur-xl border border-brand-border rounded-2xl grid grid-cols-2 lg:grid-cols-4 divide-x-0 lg:divide-x divide-y lg:divide-y-0 divide-brand-border overflow-hidden">
          {stats.map((stat) => (
            <div key={stat.label} className="flex items-center gap-4 p-6 relative group">
              <div className={`w-12 h-12 rounded-xl bg-${stat.color}/10 border border-${stat.color}/20 flex items-center justify-center shrink-0 group-hover:scale-110 transition-transform`}>
                <stat.icon className={`w-6 h-6 text-${stat.color}`} />
              </div>
              <div>
                <div className="text-2xl font-black text-white">{stat.value}</div>
                <div className="text-brand-muted text-xs mt-0.5">{stat.label}</div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
