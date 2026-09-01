"use client";
import Link from "next/link";
import { ArrowRight } from "lucide-react";
import PromoPlayer from "@/components/watch/PromoPlayer";

export default function WatchPage() {
  return (
    <section className="px-4 sm:px-6 lg:px-8 py-16">
      <div className="max-w-5xl mx-auto">
        <div className="text-center mb-10">
          <h1 className="font-black tracking-tight text-3xl sm:text-5xl text-white mb-3">
            See <span className="gradient-text">Enktel IPTV</span> in action
          </h1>
          <p className="text-brand-muted max-w-xl mx-auto">
            A 46-second look at what you get: live channels, sports &amp; PPV, the member portal, and simple pricing.
          </p>
        </div>

        <PromoPlayer />

        <div className="text-center mt-10">
          <Link
            href="/watch"
            className="inline-flex items-center gap-2 font-bold px-8 py-4 rounded-xl text-base text-white transition-all duration-300 hover:-translate-y-1"
            style={{ background: "linear-gradient(135deg, #6C63FF 0%, #5348d4 100%)" }}
          >
            Start Watching
            <ArrowRight className="w-4 h-4" />
          </Link>
        </div>
      </div>
    </section>
  );
}
