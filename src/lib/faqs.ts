import { PLANS } from "./pricing";

/** "1-month, 3-month, 6-month and 12-month", from the plan table itself. */
const PLAN_LENGTHS = (() => {
  const names = PLANS.map((p) => `${p.months}-month`);
  return names.length > 1
    ? `${names.slice(0, -1).join(", ")} and ${names[names.length - 1]}`
    : names[0];
})();

export interface FaqEntry {
  q: string;
  a: string;
}

export interface FaqCategory {
  name: string;
  faqs: FaqEntry[];
}

export const FAQ_CATEGORIES: FaqCategory[] = [
  {
    name: "Getting Started",
    faqs: [
      {
        q: "What devices does Enktel IPTV support?",
        a: "Smart TVs (Samsung, LG, Sony), Amazon Firestick, Android/iOS phones and tablets, MAG boxes, and Windows/Mac computers. Any device that supports IPTV players like TiviMate, IPTV Smarters, or VLC works with Enktel — or skip installing a player entirely and watch straight in a browser with the free Web Player at watch.enktel.tv.",
      },
      {
        q: "Do I need to download and pay for a separate IPTV player app?",
        a: "No — the Web Player at watch.enktel.tv is included free with every Enktel subscription and works in any modern browser on a laptop, desktop, or tablet. Just log in with the same details used elsewhere; no app download, install, or extra purchase needed. Third-party apps like TiviMate or IPTV Smarters are still supported if preferred, but they're optional.",
      },
      {
        q: "How do I get set up after subscribing?",
        a: "You'll receive your M3U playlist URL, EPG URL, username, and password immediately after checkout. Visit our Setup Guides page for step-by-step instructions for your device, or just log in at watch.enktel.tv to start watching immediately with no setup at all.",
      },
      {
        q: "Can I try before I buy?",
        a: "Yes — start a free 24-hour trial from the Trial page. It sets itself up: you get a playlist and login straight away, with no card and nothing to cancel. Support can still set one up by hand if you would rather ask a person.",
      },
    ],
  },
  {
    name: "Billing & Plans",
    faqs: [
      {
        q: "What payment methods do you accept?",
        a: "PayPal, and all major credit and debit cards through PayPal — no PayPal account needed for a card payment. Cryptocurrency on request. Prices are in Australian dollars.",
      },
      {
        q: "Do monthly plans auto-renew?",
        // Listed from the plan table rather than written out. This answer said
        // "monthly, 3-month, and 12-month" after the six-month pass was added,
        // which is a factual claim about the product that quietly went wrong.
        a: `No — every plan (${PLAN_LENGTHS}) is a one-time payment with no automatic renewal or recurring billing. When your subscription is about to expire, simply purchase again to keep your service running without interruption.`,
      },
      {
        q: "Can I get a refund?",
        a: "See our Refund Policy page for full details — in short, requests within 48 hours of purchase with minimal usage are generally eligible.",
      },
      {
        q: "How many devices can I use at once?",
        a: "Every plan is limited to 1 device per account at a time. Need more? Contact support about multi-device options.",
      },
    ],
  },
  {
    name: "Technical",
    faqs: [
      {
        q: "I'm getting buffering or freezing — what should I do?",
        a: "Make sure your internet connection has at least 15-25 Mbps for 4K streams. Try switching to a 5GHz Wi-Fi band or a wired connection, and lower the stream quality if your connection is limited.",
      },
      {
        q: "My playlist won't load in my IPTV player.",
        a: "Double-check you copied the full M3U URL from your dashboard with no extra spaces, and that your app supports Xtream Codes / M3U playlists. Reach out via Contact if it still won't load.",
      },
      {
        q: "Is there a limit to catch-up / replay TV?",
        a: "Yes, all plans include up to 30 days of catch-up TV on supported channels.",
      },
    ],
  },
];

export function searchFaqs(query: string, limit = 3): FaqEntry[] {
  const terms = query.toLowerCase().split(/\s+/).filter(Boolean);
  if (terms.length === 0) return [];

  const scored = FAQ_CATEGORIES.flatMap((cat) => cat.faqs).map((faq) => {
    const haystack = `${faq.q} ${faq.a}`.toLowerCase();
    const score = terms.reduce((acc, term) => acc + (haystack.includes(term) ? 1 : 0), 0);
    return { faq, score };
  });

  return scored
    .filter((s) => s.score > 0)
    .sort((a, b) => b.score - a.score)
    .slice(0, limit)
    .map((s) => s.faq);
}
