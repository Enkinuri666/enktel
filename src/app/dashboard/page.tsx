"use client";
import { useState, useEffect } from "react";
import Link from "next/link";
import { motion, AnimatePresence } from "framer-motion";
import {
  Copy, Check, Tv, ChevronRight, AlertTriangle, Sparkles, X, Clock,
  MessageCircle, HelpCircle, ChevronDown, MonitorX, LogOut,
} from "lucide-react";
import Button from "@/components/ui/Button";
import Badge from "@/components/ui/Badge";
import { StoredSubscription, loadSubscription, clearSubscription } from "@/lib/subscriptionStorage";
import { DEVICE_GUIDES, getDeviceGuide } from "@/lib/deviceGuides";

const WELCOME_DISMISSED_KEY = "enktel_dashboard_welcome_dismissed";
const TOUR_DISMISSED_KEY = "enktel_dashboard_tour_dismissed";
const whatsappNumber = process.env.NEXT_PUBLIC_WHATSAPP_NUMBER || "";

function CopyableField({ label, value, mono = true }: { label: string; value: string; mono?: boolean }) {
  const [copied, setCopied] = useState(false);

  async function copy() {
    await navigator.clipboard.writeText(value).catch(() => {});
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  }

  return (
    <div>
      <p className="text-brand-muted text-xs mb-2">{label}</p>
      <div className="flex items-center gap-2 bg-brand-bg border border-brand-border rounded-lg px-3 py-2.5">
        <span className={`text-brand-primary text-xs truncate flex-1 ${mono ? "font-mono" : ""}`}>{value}</span>
        <button
          onClick={copy}
          className="shrink-0 p-1 rounded hover:bg-white/10 transition-colors text-brand-muted hover:text-white"
        >
          {copied ? <Check className="w-4 h-4 text-green-400" /> : <Copy className="w-4 h-4" />}
        </button>
      </div>
    </div>
  );
}

const tourSteps = [
  { title: "Your Stream Credentials", description: "Your username, password, M3U and EPG URLs live here. Copy them with one click whenever you set up a new device." },
  { title: "Setup Guides", description: "Pick your device below for a step-by-step walkthrough — Firestick, Smart TV, MAG box, mobile, PC, or router." },
  { title: "Troubleshooting", description: "Buffering, black screen, or login issues? Check the troubleshooting section before reaching out — most issues are fixed in seconds." },
  { title: "24/7 WhatsApp Support", description: "Stuck? Our team is on WhatsApp around the clock. Tap the green button anytime, on any page." },
];

const troubleshootingItems = [
  {
    q: "I'm getting a black screen or the stream won't load",
    a: "Double-check your M3U URL was copied in full with no spaces. Restart your IPTV app, and make sure your internet connection has at least 15 Mbps download speed for HD or 25 Mbps for 4K.",
  },
  {
    q: "My app says \"Invalid Username or Password\"",
    a: "Credentials are case-sensitive — copy them directly from your dashboard rather than retyping. If you recently renewed, make sure you're using the latest credentials shown above, not an old saved login.",
  },
  {
    q: "Channels are buffering or freezing",
    a: "Switch to a wired Ethernet connection if possible, or move closer to your WiFi router. Lowering the stream quality in your player's settings (if available) can also help on slower connections.",
  },
  {
    q: "The EPG / program guide isn't showing",
    a: "Some apps require the EPG URL to be added separately from the M3U URL — look for an \"EPG\" or \"XMLTV\" field in your app's settings and paste it there. EPG data can also take a few minutes to load after first setup.",
  },
  {
    q: "Can I use my subscription on multiple devices?",
    a: "Your plan includes the number of connections shown in your dashboard. Using more devices simultaneously than your plan allows may cause one stream to disconnect — contact support to add connections.",
  },
  {
    q: "My trial expired — can I keep my same login?",
    a: "Upgrading to a paid plan provisions a new, permanent line, so credentials will change. We'll email your new details the moment your payment is confirmed.",
  },
];

function Troubleshooting() {
  const [open, setOpen] = useState<number | null>(null);

  return (
    <div className="bg-brand-card border border-brand-border rounded-xl p-6">
      <div className="flex items-center gap-2 mb-5">
        <MonitorX className="w-5 h-5 text-brand-primary" />
        <h2 className="text-white font-bold text-xl">Troubleshooting</h2>
      </div>
      <div className="space-y-2.5 mb-5">
        {troubleshootingItems.map((item, i) => (
          <div key={i} className="bg-brand-bg border border-brand-border rounded-lg overflow-hidden">
            <button
              onClick={() => setOpen(open === i ? null : i)}
              className="w-full flex items-center justify-between px-4 py-3 text-left"
            >
              <span className="text-white text-sm font-medium pr-4">{item.q}</span>
              <ChevronDown className={`w-4 h-4 text-brand-muted shrink-0 transition-transform ${open === i ? "rotate-180" : ""}`} />
            </button>
            {open === i && (
              <div className="px-4 pb-3 text-brand-muted text-sm leading-relaxed border-t border-brand-border pt-3">
                {item.a}
              </div>
            )}
          </div>
        ))}
      </div>
      {whatsappNumber && (
        <a
          href={`https://wa.me/${whatsappNumber}?text=${encodeURIComponent("Hi! I need help with my Enktel IPTV setup.")}`}
          target="_blank"
          rel="noopener noreferrer"
          className="flex items-center justify-center gap-2.5 bg-green-500/10 border border-green-500/30 rounded-lg p-3.5 hover:bg-green-500/15 transition-colors text-sm"
        >
          <MessageCircle className="w-4 h-4 text-green-400 shrink-0" />
          <span className="text-white font-semibold">Still stuck? Chat with us on WhatsApp — we're here 24/7</span>
        </a>
      )}
    </div>
  );
}

function DashboardTour({ onClose }: { onClose: () => void }) {
  const [step, setStep] = useState(0);
  const isLast = step === tourSteps.length - 1;

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="fixed inset-0 z-[10000] bg-black/70 flex items-center justify-center p-4"
    >
      <motion.div
        initial={{ opacity: 0, y: 20, scale: 0.96 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        className="bg-brand-card border border-brand-primary/30 rounded-2xl p-7 max-w-sm w-full shadow-2xl shadow-brand-primary/20"
      >
        <div className="flex items-center gap-2 mb-4">
          {tourSteps.map((_, i) => (
            <div key={i} className={`h-1.5 flex-1 rounded-full ${i <= step ? "bg-brand-primary" : "bg-brand-border"}`} />
          ))}
        </div>
        <h3 className="text-white font-bold text-lg mb-2">{tourSteps[step].title}</h3>
        <p className="text-brand-muted text-sm leading-relaxed mb-6">{tourSteps[step].description}</p>
        <div className="flex items-center justify-between gap-3">
          <button onClick={onClose} className="text-brand-muted text-sm hover:text-white transition-colors">
            Skip tour
          </button>
          <Button size="sm" onClick={() => (isLast ? onClose() : setStep(step + 1))}>
            {isLast ? "Got it!" : "Next"}
          </Button>
        </div>
      </motion.div>
    </motion.div>
  );
}

export default function DashboardPage() {
  const [activeTab, setActiveTab] = useState<string>(DEVICE_GUIDES[0].id);
  const [showWelcome, setShowWelcome] = useState(false);
  const [showTour, setShowTour] = useState(false);
  const [sub, setSub] = useState<StoredSubscription | null>(null);
  const [checked, setChecked] = useState(false);
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    const loaded = loadSubscription();
    setSub(loaded);
    setChecked(true);
    if (loaded?.device && getDeviceGuide(loaded.device)) setActiveTab(loaded.device);
    if (!localStorage.getItem(WELCOME_DISMISSED_KEY)) setShowWelcome(true);
    if (!localStorage.getItem(TOUR_DISMISSED_KEY)) setShowTour(true);
  }, []);

  useEffect(() => {
    if (!sub?.isTrial) return;
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, [sub?.isTrial]);

  function dismissWelcome() {
    localStorage.setItem(WELCOME_DISMISSED_KEY, "1");
    setShowWelcome(false);
  }

  function closeTour() {
    localStorage.setItem(TOUR_DISMISSED_KEY, "1");
    setShowTour(false);
  }

  function handleLogout() {
    clearSubscription();
    setSub(null);
  }

  if (checked && !sub) {
    return (
      <div className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-10">
        <h1 className="text-3xl font-bold text-white mb-8">
          My{" "}
          <span className="bg-gradient-to-r from-brand-primary to-brand-secondary bg-clip-text text-transparent">
            Dashboard
          </span>
        </h1>
        <div className="bg-brand-card border border-brand-border rounded-xl p-10 text-center">
          <Tv className="w-10 h-10 text-brand-muted mx-auto mb-4" />
          <h2 className="text-white font-bold text-xl mb-2">No active subscription</h2>
          <p className="text-brand-muted text-sm mb-6 max-w-md mx-auto">
            We couldn&apos;t find a subscription linked to this browser. If you already have an active line,
            log in with your IPTV username and password. Otherwise, get started below.
          </p>
          <div className="flex items-center justify-center gap-3 flex-wrap">
            <Link href="/login">
              <Button>Log In</Button>
            </Link>
            <Link href="/watch">
              <Button variant="outline">Start Free Trial</Button>
            </Link>
            <Link href="/watch">
              <Button variant="outline">View Plans</Button>
            </Link>
          </div>
        </div>
      </div>
    );
  }

  if (!sub) return null;

  const isTrial = Boolean(sub.isTrial);
  const endMs = new Date(sub.endDate).getTime();
  const msLeft = Math.max(0, endMs - now);
  const daysLeft = Math.floor(msLeft / 86400000);
  const hoursLeft = Math.floor((msLeft % 86400000) / 3600000);
  const minutesLeft = Math.floor((msLeft % 3600000) / 60000);
  const secondsLeft = Math.floor((msLeft % 60000) / 1000);
  const expired = msLeft <= 0;

  return (
    <div className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-10">
      <AnimatePresence>{showTour && <DashboardTour onClose={closeTour} />}</AnimatePresence>

      <div className="flex items-center justify-between gap-4 mb-8">
        <h1 className="text-3xl font-bold text-white">
          My{" "}
          <span className="bg-gradient-to-r from-brand-primary to-brand-secondary bg-clip-text text-transparent">
            Dashboard
          </span>
        </h1>
        <button
          onClick={handleLogout}
          className="flex items-center gap-1.5 text-brand-muted text-sm hover:text-white transition-colors shrink-0"
        >
          <LogOut className="w-4 h-4" /> Log out
        </button>
      </div>

      <AnimatePresence>
        {showWelcome && (
          <motion.div
            initial={{ opacity: 0, y: -10, height: 0 }}
            animate={{ opacity: 1, y: 0, height: "auto" }}
            exit={{ opacity: 0, height: 0 }}
            className="relative flex items-start gap-4 bg-gradient-to-r from-brand-primary/15 to-brand-secondary/10 border border-brand-primary/30 rounded-xl p-5 mb-8 overflow-hidden"
          >
            <Sparkles className="w-5 h-5 text-brand-secondary shrink-0 mt-0.5" />
            <div className="flex-1">
              <h3 className="text-white font-bold mb-1">
                {isTrial ? "Welcome to your 24-hour trial — let's get you streaming" : "Welcome to Enktel — let's get you streaming"}
              </h3>
              <p className="text-brand-muted text-sm leading-relaxed">
                1. Copy your M3U &amp; EPG URLs below. &nbsp; 2. Pick your device in the Setup Guides section. &nbsp;
                3. Paste the URLs into your IPTV app and you&apos;re live. Need help? We&apos;re on WhatsApp 24/7.
              </p>
            </div>
            <button
              onClick={dismissWelcome}
              aria-label="Dismiss welcome message"
              className="shrink-0 p-1 rounded text-brand-muted hover:text-white hover:bg-white/10 transition-colors"
            >
              <X className="w-4 h-4" />
            </button>
          </motion.div>
        )}
      </AnimatePresence>

      {isTrial && (
        <motion.div
          initial={{ opacity: 0, y: -10 }}
          animate={{ opacity: 1, y: 0 }}
          className={`flex items-center justify-between gap-4 rounded-xl p-4 mb-8 border ${
            expired ? "bg-red-500/10 border-red-500/30" : "bg-yellow-400/10 border-yellow-400/30"
          }`}
        >
          <div className="flex items-center gap-3">
            <Clock className={`w-5 h-5 shrink-0 ${expired ? "text-red-400" : "text-yellow-400"}`} />
            <div>
              <p className={`text-sm font-bold ${expired ? "text-red-300" : "text-yellow-200"}`}>
                {expired ? "Your free trial has ended" : "Free Trial Active"}
              </p>
              {!expired && (
                <p className="text-yellow-200/80 text-xs font-mono">
                  {daysLeft > 0 && `${daysLeft}d `}
                  {String(hoursLeft).padStart(2, "0")}:{String(minutesLeft).padStart(2, "0")}:{String(secondsLeft).padStart(2, "0")} remaining
                </p>
              )}
            </div>
          </div>
          <Link href="/watch" className="shrink-0">
            <Button size="sm">{expired ? "Reactivate Now" : "Upgrade Now"}</Button>
          </Link>
        </motion.div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 mb-8">
        {/* Subscription Status */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          className="lg:col-span-2 bg-brand-card border border-brand-border rounded-xl p-6"
        >
          <div className="flex items-start justify-between mb-5">
            <div>
              <h2 className="text-white font-bold text-xl flex items-center gap-2">
                {isTrial ? "24-Hour Trial" : `${sub.plan} Plan`}
                {isTrial && <Badge variant="warning" size="sm">FREE</Badge>}
              </h2>
              <p className="text-brand-muted text-sm">{sub.id}</p>
            </div>
            <Badge variant={expired ? "default" : "success"} size="md" className="font-bold">
              {expired ? "EXPIRED" : "ACTIVE"}
            </Badge>
          </div>

          {!isTrial && daysLeft <= 14 && !expired && (
            <div className="flex items-center justify-between gap-4 bg-yellow-400/10 border border-yellow-400/30 rounded-xl p-4 mb-5">
              <div className="flex items-center gap-3">
                <AlertTriangle className="w-5 h-5 text-yellow-400 shrink-0" />
                <p className="text-yellow-200 text-sm">
                  {daysLeft === 0
                    ? "Your subscription expires today."
                    : `Your subscription expires in ${daysLeft} day${daysLeft === 1 ? "" : "s"}.`}{" "}
                  Renew now to avoid interruption.
                </p>
              </div>
              <Link href="/watch" className="shrink-0">
                <Button size="sm">Renew Now</Button>
              </Link>
            </div>
          )}

          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 mb-5">
            {[
              { label: "Status", value: expired ? "Expired" : "Active" },
              { label: isTrial ? "Time Left" : "Days Remaining", value: isTrial ? `${hoursLeft}h ${minutesLeft}m` : daysLeft.toString() },
              { label: "Connections", value: "1/1" },
              { label: isTrial ? "Trial Ends" : "Renewal", value: new Date(sub.endDate).toLocaleDateString("en-GB") },
            ].map((item) => (
              <div key={item.label} className="bg-brand-bg border border-brand-border rounded-lg p-3">
                <p className="text-brand-muted text-xs mb-1">{item.label}</p>
                <p className="text-white font-semibold text-sm">{item.value}</p>
              </div>
            ))}
          </div>

          <div className="space-y-4">
            <div>
              <h3 className="text-white font-semibold text-sm mb-3 flex items-center gap-2">
                Login Credentials
                <span className="text-brand-muted text-xs font-normal">(for IPTV Smarters, TiviMate, etc.)</span>
              </h3>
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                <CopyableField label="Server URL" value={sub.m3uUrl.split("/get.php")[0] || "http://api.elg-26.com"} />
                <CopyableField label="Username" value={sub.username} />
                <CopyableField label="Password" value={sub.password} />
              </div>
            </div>
            <div>
              <h3 className="text-white font-semibold text-sm mb-3 flex items-center gap-2">
                Playlist URLs
                <span className="text-brand-muted text-xs font-normal">(for apps that use M3U links)</span>
              </h3>
              <div className="space-y-3">
                <CopyableField label="M3U Playlist URL" value={sub.m3uUrl} />
                <CopyableField label="EPG URL (XMLTV)" value={sub.epgUrl} />
              </div>
            </div>
          </div>
        </motion.div>

        {/* Quick Stats */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.1 }}
          className="space-y-4"
        >
          <div className="bg-brand-card border border-brand-border rounded-xl p-5">
            <h3 className="text-white font-semibold mb-3">Connection Usage</h3>
            <div className="mb-2">
              <div className="h-2 bg-brand-border rounded-full overflow-hidden">
                <div className="h-full bg-gradient-to-r from-brand-primary to-brand-secondary rounded-full w-full" />
              </div>
            </div>
            <p className="text-brand-muted text-xs">1 of 1 connections in use</p>
          </div>

          <div className="bg-brand-card border border-brand-border rounded-xl p-5">
            <h3 className="text-white font-semibold mb-3">{isTrial ? "Trial Period" : "Subscription Period"}</h3>
            <div className="mb-2">
              <div className="h-2 bg-brand-border rounded-full overflow-hidden">
                <div
                  className="h-full bg-gradient-to-r from-brand-secondary to-brand-primary rounded-full transition-all"
                  style={{ width: `${Math.max(2, Math.min(100, (msLeft / (isTrial ? 86400000 : 365 * 86400000)) * 100))}%` }}
                />
              </div>
            </div>
            <p className="text-brand-muted text-xs">
              {expired ? "Expired" : isTrial ? `${hoursLeft}h ${minutesLeft}m remaining` : `${daysLeft} days remaining`}
            </p>
          </div>

          <Link href={isTrial ? "/pricing" : "/pricing"} className="flex items-center justify-between bg-brand-primary/10 border border-brand-primary/30 rounded-xl p-4 hover:bg-brand-primary/20 transition-colors group">
            <div>
              <p className="text-white font-semibold text-sm">{isTrial ? "Upgrade to a Full Plan" : "Upgrade Plan"}</p>
              <p className="text-brand-muted text-xs">{isTrial ? "Keep streaming after your trial ends" : "Get more connections & features"}</p>
            </div>
            <ChevronRight className="w-4 h-4 text-brand-primary group-hover:translate-x-1 transition-transform" />
          </Link>
        </motion.div>
      </div>

      {/* Setup Guides */}
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.15 }}
        className="bg-brand-card border border-brand-border rounded-xl p-6 mb-6"
      >
        <h2 className="text-white font-bold text-xl mb-5">Device Setup Guides</h2>

        {/* Tabs */}
        <div className="flex items-center gap-2 overflow-x-auto pb-2 mb-6 scrollbar-thin">
          {DEVICE_GUIDES.map((guide) => (
            <button
              key={guide.id}
              onClick={() => setActiveTab(guide.id)}
              className={`flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium whitespace-nowrap transition-colors shrink-0 ${
                activeTab === guide.id
                  ? "bg-brand-primary text-white"
                  : "bg-brand-bg border border-brand-border text-brand-muted hover:text-white hover:border-brand-primary/40"
              }`}
            >
              <guide.icon className="w-4 h-4" />
              {guide.label}
            </button>
          ))}
        </div>

        {/* Steps */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          {(getDeviceGuide(activeTab)?.steps || []).map((step) => (
            <div
              key={step.step}
              className="flex gap-4 bg-brand-bg border border-brand-border rounded-xl p-4"
            >
              <div className="w-8 h-8 bg-brand-primary/20 border border-brand-primary/30 rounded-full flex items-center justify-center text-brand-primary font-bold text-sm shrink-0">
                {step.step}
              </div>
              <div>
                <h4 className="text-white font-semibold text-sm mb-1">{step.title}</h4>
                <p className="text-brand-muted text-xs leading-relaxed">{step.description}</p>
              </div>
            </div>
          ))}
        </div>
      </motion.div>

      {/* Troubleshooting */}
      <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.2 }}>
        <Troubleshooting />
      </motion.div>

      <div className="flex items-center justify-center mt-6">
        <button
          onClick={() => { localStorage.removeItem(TOUR_DISMISSED_KEY); setShowTour(true); }}
          className="flex items-center gap-2 text-brand-muted text-xs hover:text-white transition-colors"
        >
          <HelpCircle className="w-3.5 h-3.5" /> Replay dashboard tour
        </button>
      </div>
    </div>
  );
}
