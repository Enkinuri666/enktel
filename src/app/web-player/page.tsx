import { Metadata } from "next";
import MagazineNav from "@/components/magazine/MagazineNav";
import MagazineCrossLinks from "@/components/magazine/MagazineCrossLinks";
import WebPlayerHero from "@/components/webplayer/WebPlayerHero";
import WebPlayerEcosystem from "@/components/webplayer/WebPlayerEcosystem";
import WebPlayerFeatures from "@/components/webplayer/WebPlayerFeatures";
import WebPlayerScreenshots from "@/components/webplayer/WebPlayerScreenshots";
import WebPlayerHowTo from "@/components/webplayer/WebPlayerHowTo";
import WebPlayerCTA from "@/components/webplayer/WebPlayerCTA";

export const metadata: Metadata = {
  title: "Enktel Web Player — Watch Live TV in Your Browser",
  description:
    "Introducing the new Enktel Web Player at watch.enktel.tv: live channels and a full TV guide, streaming right in your browser. Included free with every Enktel subscription — just log in with your existing IPTV details.",
};

export default function WebPlayerPage() {
  return (
    <>
      <MagazineNav
        active="web-player"
        kicker="Feature Story"
        title="The Enktel Web Player is here"
        description="Live channels and the full program guide, streaming straight in your browser — no app, no extra device, no extra charge."
      />
      <WebPlayerHero />
      <WebPlayerEcosystem />
      <WebPlayerFeatures />
      <WebPlayerScreenshots />
      <WebPlayerHowTo />
      <WebPlayerCTA />
      <MagazineCrossLinks active="web-player" />
    </>
  );
}
