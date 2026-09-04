import { CHANNEL_COUNT_LABEL } from "@/lib/catalogSize";
/**
 * The subscription plans, and the one place their numbers live.
 *
 * Pricing appears on the homepage, the pricing page, the dashboard's renewal
 * panel and the checkout that takes the money. Those drifting apart is not a
 * cosmetic bug — a page advertising $139.99 beside a checkout charging
 * something else is the kind of thing that ends in a chargeback — so every
 * surface reads from here and nothing hardcodes an amount.
 *
 * ## Why the derived figures are computed rather than written down
 *
 * The "just $11.66/month" and "save 40%" lines are advertising claims about
 * these amounts. Written by hand they can be wrong, and a wrong one is wrong
 * in public. [perMonth] and [savingPercent] compute them, and both round
 * **down** — the monthly figure to the cent and the saving to the point — so
 * a rounding error can only ever understate the offer. Never the reverse.
 */

export const CURRENCY = "AUD" as const;

/** Shown next to amounts. AUD and USD both use "$", so the code has to appear. */
export const CURRENCY_SYMBOL = "$";

export interface Plan {
  /** Stable id. Used by checkout and stored against an order — do not reword. */
  id: "1m" | "3m" | "6m" | "12m";
  /** Whole months of access. */
  months: number;
  /** Short name for the card heading. */
  name: string;
  /** The marketing label under the name. */
  tagline: string;
  /** What is actually charged, in dollars. */
  price: number;
  /** The struck-through "was" figure. Never below [price]. */
  wasPrice: number;
  /** One line under the price explaining who it is for. */
  blurb: string;
  /** Bullets specific to this tier, beyond what every tier includes. */
  perks: string[];
  /** At most one plan carries this. */
  highlight?: "popular" | "best-value";
}

/**
 * Every tier includes these, so they are listed once rather than repeated
 * four times and drifting.
 */
export const INCLUDED_IN_EVERY_PLAN: string[] = [
  `Instant access to ${CHANNEL_COUNT_LABEL} live global channels`,
  "All live sports — AFL, NRL, EPL, NFL, NBA",
  "Full PPV event access — UFC, boxing, WWE",
  "Complete video-on-demand movie & TV library",
  "Zero setup fees, delivered instantly",
];

export const PLANS: Plan[] = [
  {
    id: "1m",
    months: 1,
    name: "1-Month Pass",
    tagline: "The Starter",
    price: 19.99,
    wasPrice: 29.99,
    blurb:
      "Perfect for casual viewers wanting to test the ultimate streaming experience.",
    perks: [],
  },
  {
    id: "3m",
    months: 3,
    name: "3-Month Pass",
    tagline: "The Finals Season Pass",
    price: 49.99,
    wasPrice: 75.0,
    blurb:
      "Secure uninterrupted 4K coverage for the entire footy finals and racing season.",
    perks: ["Guaranteed buffer-free premium Eagle 4K server routing"],
    highlight: "popular",
  },
  {
    id: "6m",
    months: 6,
    name: "6-Month Pass",
    tagline: "The Half-Year Saver",
    price: 79.99,
    wasPrice: 120.0,
    blurb:
      "Cut your monthly entertainment budget down to the price of a single takeaway lunch.",
    perks: ["Dedicated priority customer support"],
  },
  {
    id: "12m",
    months: 12,
    name: "12-Month All-Access",
    tagline: "The Ultimate VIP",
    price: 139.99,
    wasPrice: 160.0,
    blurb:
      "The ultimate cord-cutter package. Replace Foxtel, Kayo and Netflix for less than $12 a month.",
    perks: [
      "Locks in your discounted rate for a full calendar year",
      "Free automatic VOD library updates every week",
    ],
    highlight: "best-value",
  },
];

/** The monthly plan, which the longer ones are measured against. */
export const BASE_MONTHLY = PLANS[0];

export function planById(id: string): Plan | undefined {
  return PLANS.find((p) => p.id === id);
}

/**
 * Cost per month, rounded **down** to the cent.
 *
 * Down rather than nearest, because this number is the headline claim on the
 * card. Rounding 13.331 up to 13.34 would advertise a monthly rate nobody is
 * actually offered; rounding down cannot overstate the deal.
 */
export function perMonth(plan: Plan): number {
  return Math.floor((plan.price / plan.months) * 100) / 100;
}

/**
 * How much cheaper than paying monthly, as a whole percent, rounded **down**.
 *
 * Zero for the monthly plan itself — it is not cheaper than itself, and a
 * "save 0%" badge should not render.
 */
export function savingPercent(plan: Plan): number {
  if (plan.months <= 1) return 0;
  const monthlyTotal = BASE_MONTHLY.price * plan.months;
  if (monthlyTotal <= 0) return 0;
  return Math.floor(((monthlyTotal - plan.price) / monthlyTotal) * 100);
}

/** What paying monthly for the same span would cost. */
export function monthlyEquivalent(plan: Plan): number {
  return Math.round(BASE_MONTHLY.price * plan.months * 100) / 100;
}

/** "$139.99" — two decimals always, so a price never renders as "$140". */
export function formatPrice(amount: number): string {
  return `${CURRENCY_SYMBOL}${amount.toFixed(2)}`;
}

/** "$139.99 AUD", for the places where the currency has to be unambiguous. */
export function formatPriceWithCurrency(amount: number): string {
  return `${formatPrice(amount)} ${CURRENCY}`;
}

/** How long the free trial runs, in hours. */
export const TRIAL_HOURS = 24;
