import Hero from "@/components/home/Hero";
import WorldCupTicker from "@/components/home/WorldCupTicker";
import StatsBar from "@/components/home/StatsBar";
import CroatianPromo from "@/components/home/CroatianPromo";
import EPGTeaser from "@/components/home/EPGTeaser";
import SportsBanner from "@/components/home/SportsBanner";
import UpcomingEventsWidget from "@/components/home/UpcomingEventsWidget";
import LatestReleases from "@/components/home/LatestReleases";
import ComingSoonWidget from "@/components/home/ComingSoonWidget";
import ChannelShowcase from "@/components/home/ChannelShowcase";
import Features from "@/components/home/Features";
import PricingPreview from "@/components/home/PricingPreview";
import Testimonials from "@/components/home/Testimonials";

export default function HomePage() {
  return (
    <>
      <Hero />
      <WorldCupTicker />
      <StatsBar />
      <CroatianPromo />
      <EPGTeaser />
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
