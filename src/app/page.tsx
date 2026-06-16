import Hero from "@/components/home/Hero";
import StatsBar from "@/components/home/StatsBar";
import WhatsOnWidget from "@/components/home/WhatsOnWidget";
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
      <StatsBar />
      <WhatsOnWidget />
      <LatestReleases />
      <ComingSoonWidget />
      <ChannelShowcase />
      <Features />
      <PricingPreview />
      <Testimonials />
    </>
  );
}
