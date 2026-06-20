import Link from "next/link";
import { Zap, Twitter, Facebook, Instagram, Youtube } from "lucide-react";
import NewsletterForm from "./NewsletterForm";
import { CHANNEL_COUNT_LABEL } from "@/lib/channels";

const quickLinks = [
  { href: "/", label: "Home" },
  { href: "/channels", label: "Channel List" },
  { href: "/epg", label: "EPG Guide" },
  { href: "/whats-on", label: "What's On" },
  { href: "/latest-releases", label: "Latest Releases" },
  { href: "/coming-soon", label: "Coming Soon" },
  { href: "/pricing", label: "Pricing" },
];

const supportLinks = [
  { href: "/dashboard", label: "My Dashboard" },
  { href: "/checkout", label: "Subscribe Now" },
  { href: "/setup-guides", label: "Setup Guides" },
  { href: "/contact", label: "Contact Support" },
  { href: "/faqs", label: "FAQs" },
  { href: "/status", label: "Status Page" },
];

const legalLinks = [
  { href: "/privacy", label: "Privacy Policy" },
  { href: "/terms", label: "Terms of Service" },
  { href: "/refund-policy", label: "Refund Policy" },
  { href: "/cookie-policy", label: "Cookie Policy" },
  { href: "/dmca", label: "DMCA" },
];

export default function Footer() {
  return (
    <footer className="bg-brand-card border-t border-brand-border mt-20">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Main Footer */}
        <div className="py-12 grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-8">
          {/* Brand Column */}
          <div className="lg:col-span-2">
            <Link href="/" className="flex items-center gap-2 mb-4">
              <div className="w-8 h-8 bg-gradient-to-br from-brand-primary to-brand-secondary rounded-lg flex items-center justify-center">
                <Zap className="w-5 h-5 text-white fill-white" />
              </div>
              <span className="text-xl font-bold">
                <span className="text-white">ENK</span>
                <span className="text-brand-primary">TEL</span>
                <span className="text-brand-muted text-sm font-normal ml-1">IPTV</span>
              </span>
            </Link>
            <p className="text-brand-muted text-sm leading-relaxed mb-4 max-w-xs">
              Stream Beyond Limits. Premium IPTV service with {CHANNEL_COUNT_LABEL} channels, 4K quality, and 99.9% uptime. Your ultimate streaming companion.
            </p>
            <p className="text-brand-muted/60 text-xs mb-6">
              &quot;Stream Beyond Limits&quot;
            </p>
            {/* Social Links */}
            <div className="flex items-center gap-3">
              {[
                { icon: Twitter, href: "#", label: "Twitter" },
                { icon: Facebook, href: "#", label: "Facebook" },
                { icon: Instagram, href: "#", label: "Instagram" },
                { icon: Youtube, href: "#", label: "YouTube" },
              ].map(({ icon: Icon, href, label }) => (
                <a
                  key={label}
                  href={href}
                  aria-label={label}
                  className="w-9 h-9 rounded-lg bg-white/5 border border-brand-border flex items-center justify-center text-brand-muted hover:text-brand-primary hover:border-brand-primary/50 transition-colors"
                >
                  <Icon className="w-4 h-4" />
                </a>
              ))}
            </div>
          </div>

          {/* Quick Links */}
          <div>
            <h3 className="text-white font-semibold text-sm mb-4">Quick Links</h3>
            <ul className="space-y-2">
              {quickLinks.map((link) => (
                <li key={link.href}>
                  <Link
                    href={link.href}
                    className="text-brand-muted hover:text-white text-sm transition-colors"
                  >
                    {link.label}
                  </Link>
                </li>
              ))}
            </ul>
          </div>

          {/* Support */}
          <div>
            <h3 className="text-white font-semibold text-sm mb-4">Support</h3>
            <ul className="space-y-2">
              {supportLinks.map((link) => (
                <li key={link.label}>
                  <Link
                    href={link.href}
                    className="text-brand-muted hover:text-white text-sm transition-colors"
                  >
                    {link.label}
                  </Link>
                </li>
              ))}
            </ul>
          </div>

          {/* Legal + Newsletter */}
          <div>
            <h3 className="text-white font-semibold text-sm mb-4">Legal</h3>
            <ul className="space-y-2 mb-6">
              {legalLinks.map((link) => (
                <li key={link.label}>
                  <Link
                    href={link.href}
                    className="text-brand-muted hover:text-white text-sm transition-colors"
                  >
                    {link.label}
                  </Link>
                </li>
              ))}
            </ul>
            {/* Newsletter */}
            <div>
              <h3 className="text-white font-semibold text-sm mb-3">Newsletter</h3>
              <NewsletterForm />
            </div>
          </div>
        </div>

        {/* Bottom Bar */}
        <div className="border-t border-brand-border py-6 flex flex-col sm:flex-row items-center justify-between gap-4">
          <p className="text-brand-muted text-xs">
            &copy; {new Date().getFullYear()} Enktel IPTV. All rights reserved.
          </p>
          <p className="text-brand-muted/50 text-xs text-center">
            All trademarks, channel logos, and brand names belong to their respective owners. Enktel IPTV is a reseller service.
          </p>
        </div>
      </div>
    </footer>
  );
}
