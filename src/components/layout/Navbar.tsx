"use client";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useState } from "react";
import { Menu, X, Zap, Search } from "lucide-react";
import Button from "@/components/ui/Button";
import { clsx } from "clsx";

const navLinks = [
  { href: "/", label: "Home" },
  { href: "/channels", label: "Channels" },
  { href: "/whats-new", label: "What's New" },
  { href: "/epg", label: "EPG Guide" },
  { href: "/whats-on", label: "What's On" },
  { href: "/latest-releases", label: "Latest Releases" },
  { href: "/coming-soon", label: "Coming Soon" },
  { href: "/pricing", label: "Pricing" },
];

export default function Navbar() {
  const pathname = usePathname();
  const router = useRouter();
  const [mobileOpen, setMobileOpen] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const [searchValue, setSearchValue] = useState("");

  function handleSearchSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!searchValue.trim()) return;
    router.push(`/search?q=${encodeURIComponent(searchValue.trim())}`);
    setSearchOpen(false);
    setSearchValue("");
  }

  return (
    <nav className="fixed top-0 left-0 right-0 z-50 bg-brand-bg/80 backdrop-blur-xl border-b border-brand-secondary/15 shadow-[0_1px_20px_rgba(0,212,255,0.08)]">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-16">
          {/* Logo */}
          <Link href="/" className="flex items-center gap-2 group">
            <div className="icon-glow relative w-8 h-8 bg-gradient-to-br from-brand-primary to-brand-secondary rounded-lg flex items-center justify-center shadow-lg shadow-brand-primary/30 group-hover:shadow-brand-primary/50 transition-shadow">
              <Zap className="w-5 h-5 text-white fill-white" />
            </div>
            <span className="text-xl font-display font-bold">
              <span className="text-white">ENK</span>
              <span className="text-brand-primary">TEL</span>
              <span className="text-brand-muted text-sm font-normal ml-1">IPTV</span>
            </span>
          </Link>

          {/* Desktop Nav */}
          <div className="hidden lg:flex items-center gap-1">
            {navLinks.map((link) => (
              <Link
                key={link.href}
                href={link.href}
                className={clsx(
                  "relative px-3 py-2 rounded-lg text-sm font-medium transition-colors",
                  pathname === link.href
                    ? "text-brand-secondary text-neon-cyan bg-brand-primary/10"
                    : "text-brand-muted hover:text-white hover:bg-white/5"
                )}
              >
                {link.label}
                {pathname === link.href && (
                  <span className="absolute left-3 right-3 -bottom-px h-px bg-gradient-to-r from-brand-primary to-brand-secondary" />
                )}
              </Link>
            ))}
          </div>

          {/* CTA + Mobile Toggle */}
          <div className="flex items-center gap-3">
            {searchOpen ? (
              <form onSubmit={handleSearchSubmit} className="hidden sm:block">
                <input
                  autoFocus
                  value={searchValue}
                  onChange={(e) => setSearchValue(e.target.value)}
                  onBlur={() => !searchValue && setSearchOpen(false)}
                  placeholder="Search..."
                  className="w-48 cyber-panel rounded-full px-4 py-1.5 text-sm text-white placeholder:text-brand-muted focus:outline-none focus:border-brand-primary/40"
                />
              </form>
            ) : (
              <button
                onClick={() => setSearchOpen(true)}
                className="hidden sm:flex p-2 rounded-lg text-brand-muted hover:text-white hover:bg-white/10 transition-colors"
                aria-label="Search"
              >
                <Search className="w-4 h-4" />
              </button>
            )}
            <Link href="/pricing" className="hidden sm:block">
              <Button size="sm" variant="glow">Get Started</Button>
            </Link>
            <button
              onClick={() => setMobileOpen(!mobileOpen)}
              className="lg:hidden p-2 rounded-lg text-brand-muted hover:text-white hover:bg-white/10 transition-colors"
            >
              {mobileOpen ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
            </button>
          </div>
        </div>
      </div>

      {/* Mobile Menu */}
      {mobileOpen && (
        <div className="lg:hidden border-t border-brand-border bg-brand-bg/95 backdrop-blur-xl">
          <div className="max-w-7xl mx-auto px-4 py-3 space-y-1">
            <form
              onSubmit={(e) => { handleSearchSubmit(e); setMobileOpen(false); }}
              className="mb-2"
            >
              <input
                value={searchValue}
                onChange={(e) => setSearchValue(e.target.value)}
                placeholder="Search channels, movies..."
                className="w-full bg-brand-bg border border-brand-border rounded-lg px-3 py-2 text-sm text-white placeholder:text-brand-muted focus:outline-none focus:border-brand-primary/40"
              />
            </form>
            {navLinks.map((link) => (
              <Link
                key={link.href}
                href={link.href}
                onClick={() => setMobileOpen(false)}
                className={clsx(
                  "block px-3 py-2.5 rounded-lg text-sm font-medium transition-colors",
                  pathname === link.href
                    ? "text-brand-primary bg-brand-primary/10"
                    : "text-brand-muted hover:text-white hover:bg-white/5"
                )}
              >
                {link.label}
              </Link>
            ))}
            <div className="pt-2 pb-1">
              <Link href="/pricing" onClick={() => setMobileOpen(false)}>
                <Button fullWidth size="sm">Get Started</Button>
              </Link>
            </div>
          </div>
        </div>
      )}
    </nav>
  );
}
