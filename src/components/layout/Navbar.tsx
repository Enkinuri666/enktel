"use client";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import Image from "next/image";
import { useEffect, useState } from "react";
import { Menu, X, Search } from "lucide-react";
import Button from "@/components/ui/Button";
import { clsx } from "clsx";
import { loadSubscription, clearSubscription, SUBSCRIPTION_CHANGED_EVENT } from "@/lib/subscriptionStorage";

const navLinks = [
  { href: "/", label: "Home" },
  { href: "/channels", label: "Channels" },
  { href: "/whats-new", label: "What's New" },
  { href: "/epg", label: "EPG Guide" },
  { href: "/latest-releases", label: "Latest Releases" },
  { href: "/coming-soon", label: "Coming Soon" },
  { href: "/pricing", label: "Pricing" },
  { href: "/trial", label: "Free Trial" },
];

export default function Navbar() {
  const pathname = usePathname();
  const router = useRouter();
  const [mobileOpen, setMobileOpen] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const [searchValue, setSearchValue] = useState("");
  const [loggedIn, setLoggedIn] = useState(false);

  useEffect(() => {
    setLoggedIn(Boolean(loadSubscription()));
  }, [pathname]);

  useEffect(() => {
    const sync = () => setLoggedIn(Boolean(loadSubscription()));
    window.addEventListener(SUBSCRIPTION_CHANGED_EVENT, sync);
    window.addEventListener("storage", sync);
    return () => {
      window.removeEventListener(SUBSCRIPTION_CHANGED_EVENT, sync);
      window.removeEventListener("storage", sync);
    };
  }, []);

  function handleSearchSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!searchValue.trim()) return;
    router.push(`/search?q=${encodeURIComponent(searchValue.trim())}`);
    setSearchOpen(false);
    setSearchValue("");
  }

  function handleLogout() {
    clearSubscription();
    setLoggedIn(false);
    setMobileOpen(false);
    router.push("/");
  }

  return (
    <nav className="border-b border-brand-border/50 bg-brand-bg/80 backdrop-blur-xl">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-16">
          {/* Logo */}
          <Link href="/" className="flex items-center gap-2.5 group">
            <Image
              src="/logo-icon.png"
              alt="Enktel IPTV"
              width={36}
              height={36}
              className="w-9 h-9 drop-shadow-[0_0_10px_rgba(0,212,255,0.35)] group-hover:scale-105 transition-transform"
              priority
            />
            <span className="text-xl font-bold flex items-center gap-1.5">
              <span className="text-white">Enk</span>
              <span className="text-brand-secondary">Tel</span>
              <span className="text-[10px] font-bold rounded-full px-2 py-0.5 ml-0.5 bg-brand-secondary text-[#06122B]">IPTV</span>
            </span>
          </Link>

          {/* Desktop Nav */}
          <div className="hidden lg:flex items-center gap-1">
            {navLinks.map((link) => (
              <Link
                key={link.href}
                href={link.href}
                className={clsx(
                  "px-3 py-2 rounded-lg text-sm font-medium transition-colors",
                  pathname === link.href
                    ? "text-brand-primary bg-brand-primary/10"
                    : "text-brand-muted hover:text-white hover:bg-white/5"
                )}
              >
                {link.label}
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
                  className="w-48 bg-brand-card border border-brand-border rounded-full px-4 py-1.5 text-sm text-white placeholder:text-brand-muted focus:outline-none focus:border-brand-primary/40"
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
            {loggedIn ? (
              <button
                onClick={handleLogout}
                className="hidden sm:block text-sm font-medium text-brand-muted hover:text-white transition-colors"
              >
                Log Out
              </button>
            ) : (
              <Link
                href="/login"
                className="hidden sm:block text-sm font-medium text-brand-muted hover:text-white transition-colors"
              >
                Log In
              </Link>
            )}
            <Link href={loggedIn ? "/dashboard" : "/pricing"} className="hidden sm:block">
              <Button size="sm">{loggedIn ? "My Dashboard" : "Get Started"}</Button>
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
        <div className="lg:hidden border-t border-brand-border/50 bg-brand-bg/95 backdrop-blur-xl">
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
            {loggedIn ? (
              <button
                onClick={handleLogout}
                className="block w-full text-left px-3 py-2.5 rounded-lg text-sm font-medium text-brand-muted hover:text-white hover:bg-white/5 transition-colors"
              >
                Log Out
              </button>
            ) : (
              <Link
                href="/login"
                onClick={() => setMobileOpen(false)}
                className="block px-3 py-2.5 rounded-lg text-sm font-medium text-brand-muted hover:text-white hover:bg-white/5 transition-colors"
              >
                Log In
              </Link>
            )}
            <div className="pt-2 pb-1">
              <Link href={loggedIn ? "/dashboard" : "/pricing"} onClick={() => setMobileOpen(false)}>
                <Button fullWidth size="sm">{loggedIn ? "My Dashboard" : "Get Started"}</Button>
              </Link>
            </div>
          </div>
        </div>
      )}
    </nav>
  );
}
