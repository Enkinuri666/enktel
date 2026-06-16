import Hero from "@/components/home/Hero";
import CountryBanner from "@/components/home/CountryBanner";
import StatsBar from "@/components/home/StatsBar";
import CroatianPromo from "@/components/home/CroatianPromo";
import WhatsOnWidget from "@/components/home/WhatsOnWidget";
import SportsBanner from "@/components/home/SportsBanner";
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
      <CountryBanner />
      <StatsBar />
      <CroatianPromo />
      <WhatsOnWidget />
      <SportsBanner />
      <LatestReleases />
      <ComingSoonWidget />
      <ChannelShowcase />
      <Features />
      <PricingPreview />
      <Testimonials />
    </>
  );
}
