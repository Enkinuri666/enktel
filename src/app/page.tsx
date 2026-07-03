import Hero from "@/components/home/Hero";
import WorldCupTicker from "@/components/home/WorldCupTicker";
import StatsBar from "@/components/home/StatsBar";
import TrialBanner from "@/components/home/TrialBanner";
import WebPlayerBanner from "@/components/webplayer/WebPlayerBanner";
import CroatianPromo from "@/components/home/CroatianPromo";
import EPGTeaser from "@/components/home/EPGTeaser";
import SportsBanner from "@/components/home/SportsBanner";
import UpcomingEventsWidget from "@/components/home/UpcomingEventsWidget";
import LatestReleases from "@/components/home/LatestReleases";
import ComingSoonWidget from "@/components/home/ComingSoonWidget";
import ChannelShowcase from "@/components/home/ChannelShowcase";
import PlatformsCarousel from "@/components/home/PlatformsCarousel";
import Features from "@/components/home/Features";
import DashboardExplainer from "@/components/home/DashboardExplainer";
import PricingPreview from "@/components/home/PricingPreview";
import Testimonials from "@/components/home/Testimonials";

export default function HomePage() {
  return (
    <>
      <Hero />
      <WorldCupTicker />
      <StatsBar />
      <WebPlayerBanner />
      <TrialBanner />
      <CroatianPromo />
      <EPGTeaser />
      <SportsBanner />
      <UpcomingEventsWidget />
      <LatestReleases />
      <ComingSoonWidget />
      <ChannelShowcase />
      <PlatformsCarousel />
      <Features />
      <DashboardExplainer />
      <PricingPreview />
      <Testimonials />
    </>
  );
}
