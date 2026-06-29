"use client";
import { useState } from "react";
import { ChevronDown, HelpCircle } from "lucide-react";

const categories = [
  {
    name: "Getting Started",
    faqs: [
      {
        q: "What devices does Enktel IPTV support?",
        a: "Smart TVs (Samsung, LG, Sony), Amazon Firestick, Android/iOS phones and tablets, MAG boxes, and Windows/Mac computers. Any device that supports IPTV players like TiviMate, IPTV Smarters, or VLC works with Enktel.",
      },
      {
        q: "How do I get set up after subscribing?",
        a: "You'll receive your M3U playlist URL, EPG URL, username, and password immediately after checkout. Visit our Setup Guides page for step-by-step instructions for your device.",
      },
      {
        q: "Can I try before I buy?",
        a: "Yes — contact our support team via WhatsApp or the Contact page for a free 24-hour trial.",
      },
    ],
  },
  {
    name: "Billing & Plans",
    faqs: [
      {
        q: "What payment methods do you accept?",
        a: "PayPal and all major credit/debit cards. Cryptocurrency available on request.",
      },
      {
        q: "Do monthly plans auto-renew?",
        a: "No — every plan (monthly, 3-month, and 12-month) is a one-time payment with no automatic renewal or recurring billing. When your subscription is about to expire, simply purchase again to keep your service running without interruption.",
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

export default function FaqsPage() {
  const [open, setOpen] = useState<string | null>(null);

  return (
    <div className="max-w-3xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
      <div className="text-center mb-10">
        <div className="w-14 h-14 bg-brand-primary/10 border border-brand-primary/30 rounded-full flex items-center justify-center mx-auto mb-4">
          <HelpCircle className="w-7 h-7 text-brand-primary" />
        </div>
        <h1 className="text-3xl sm:text-4xl font-black text-white mb-3">Frequently Asked Questions</h1>
        <p className="text-brand-muted">
          Can&apos;t find what you&apos;re looking for?{" "}
          <a href="/contact" className="text-brand-primary hover:underline">Contact our support team</a>.
        </p>
      </div>

      {categories.map((cat) => (
        <div key={cat.name} className="mb-8">
          <h2 className="text-white font-bold text-lg mb-3">{cat.name}</h2>
          <div className="space-y-3">
            {cat.faqs.map((faq) => {
              const id = `${cat.name}-${faq.q}`;
              const isOpen = open === id;
              return (
                <div key={id} className="bg-brand-card border border-brand-border rounded-xl overflow-hidden">
                  <button
                    onClick={() => setOpen(isOpen ? null : id)}
                    className="w-full flex items-center justify-between px-5 py-4 text-left"
                  >
                    <span className="text-white font-medium text-sm pr-4">{faq.q}</span>
                    <ChevronDown className={`w-4 h-4 text-brand-muted shrink-0 transition-transform ${isOpen ? "rotate-180" : ""}`} />
                  </button>
                  {isOpen && (
                    <div className="px-5 pb-4 text-brand-muted text-sm leading-relaxed border-t border-brand-border pt-3">
                      {faq.a}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      ))}
    </div>
  );
}
