import Hero from "@/components/home/Hero";
import StatsBar from "@/components/home/StatsBar";
import CroatianPromo from "@/components/home/CroatianPromo";
import WhatsOnWidget from "@/components/home/WhatsOnWidget";
import SportsBanner from "@/components/home/SportsBanner";
import UpcomingEventsWidget from "@/components/home/UpcomingEventsWidget";
import LatestReleases from "@/components/home/LatestReleases";
import ComingSoonWidget from "@/components/home/ComingSoonWidget";
import ChannelShowcase from "@/components/home/ChannelShowcase";
import Features from "@/components/home/Features";
import PricingPreview from "@/components/home/PricingPreview";
import Testimonials from "@/components/home/Testimonials";
import PromoStrip from "@/components/home/PromoStrip";
import ChannelMarquee from "@/components/home/ChannelMarquee";
import { Trophy, CalendarClock } from "lucide-react";

export default function HomePage() {
  return (
    <>
      <Hero />
      <PromoStrip
        icon={Trophy}
        eyebrow="Limited Time"
        title="Every FIFA World Cup 2026 match, live in 4K"
        subtitle="Included free with the 3 & 12-month plans — no separate sports package needed."
        ctaLabel="See the schedule"
        ctaHref="/world-cup-2026"
        theme="gold"
      />
      <StatsBar />
      <ChannelMarquee />
      <CroatianPromo />
      <PromoStrip
        icon={CalendarClock}
        eyebrow="Right Now"
        title="See what's airing live across every channel"
        subtitle="Real-time program guide for all 10,000+ channels — never miss your show again."
        ctaLabel="Open EPG"
        ctaHref="/whats-on"
        theme="cyan"
      />
      <WhatsOnWidget />
      <SportsBanner />
      <UpcomingEventsWidget />
      <LatestReleases />
      <ComingSoonWidget />
      <ChannelShowcase />
      <Features />
      <PricingPreview />
      <Testimonials />
    </>
  );
}
