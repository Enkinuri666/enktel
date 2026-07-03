"use client";
import { useState } from "react";
import { ChevronDown, HelpCircle } from "lucide-react";
import { FAQ_CATEGORIES as categories } from "@/lib/faqs";

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
