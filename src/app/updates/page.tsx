import { Metadata } from "next";
import MagazineNav from "@/components/magazine/MagazineNav";
import MagazineCrossLinks from "@/components/magazine/MagazineCrossLinks";
import UpdatesHero from "@/components/updates/UpdatesHero";
import UpdatesStoryGrid from "@/components/updates/UpdatesStoryGrid";

export const metadata: Metadata = {
  title: "Ecosystem Update — AI Assistant, Web Player & What's New at Enktel",
  description:
    "The latest additions to the Enktel ecosystem: Ask Enktel AI, the new Web Player at watch.enktel.tv, a refreshed neon design, and more — all in one place.",
};

export default function UpdatesPage() {
  return (
    <>
      <MagazineNav
        active="updates"
        kicker="Cover Story · This Issue"
        title="Everything new across the Enktel ecosystem"
        description="An AI assistant that actually knows the guide, a web player for every browser, and a refreshed look site-wide — here's what shipped."
      />
      <UpdatesHero />
      <UpdatesStoryGrid />
      <MagazineCrossLinks active="updates" />
    </>
  );
}
