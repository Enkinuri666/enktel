import { Metadata } from "next";
import WebPlayerHero from "@/components/webplayer/WebPlayerHero";
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
      <WebPlayerHero />
      <WebPlayerFeatures />
      <WebPlayerScreenshots />
      <WebPlayerHowTo />
      <WebPlayerCTA />
    </>
  );
}
