import { CHANNEL_COUNT_LABEL } from "@/lib/catalogSize";
import type { Metadata } from "next";
import Pricing from "@/components/home/Pricing";
import Comparison from "@/components/home/Comparison";
import { CURRENCY, PLANS, formatPrice, TRIAL_HOURS } from "@/lib/pricing";

export const metadata: Metadata = {
  title: "Pricing & Plans | Enktel IPTV",
  description: `Enktel access passes from ${formatPrice(PLANS[0].price)} ${CURRENCY}. ${CHANNEL_COUNT_LABEL} live channels, every PPV event, and a full VOD library. Free ${TRIAL_HOURS}-hour trial, no lock-in contract.`,
};

export default function PricingPage() {
  return (
    <main className="pt-8">
      <Pricing />
      <Comparison />
    </main>
  );
}
