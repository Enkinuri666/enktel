"use client";
import { useState } from "react";
import Link from "next/link";
import { Copy, Check, Tv, Smartphone, Monitor, Wifi, ChevronRight, AlertTriangle } from "lucide-react";
import Button from "@/components/ui/Button";

const MOCK_SUBSCRIPTION = {
  id: "ENK-1234567890-DEMO",
  plan: "Pro",
  status: "active",
  username: "demo_user",
  password: "Demo1234",
  startDate: "2024-01-15",
  endDate: "2025-01-15",
  m3uUrl: "https://e4kpremuim.com/get.php?username=demo_user&password=Demo1234&type=m3u_plus&output=ts",
  epgUrl: "https://e4kpremuim.com/xmltv.php?username=demo_user&password=Demo1234",
  connections: 1,
  usedConnections: 1,
};

function CopyableUrl({ label, url }: { label: string; url: string }) {
  const [copied, setCopied] = useState(false);

  async function copy() {
    await navigator.clipboard.writeText(url).catch(() => {});
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  }

  return (
    <div>
      <p className="text-brand-muted text-xs mb-2">{label}</p>
      <div className="flex items-center gap-2 bg-brand-bg border border-brand-border rounded-lg px-3 py-2.5">
        <span className="text-brand-primary text-xs font-mono truncate flex-1">{url}</span>
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

const setupTabs = [
  { id: "firestick", label: "Firestick", icon: Tv },
  { id: "smart-tv", label: "Smart TV", icon: Tv },
  { id: "mag", label: "MAG Box", icon: Tv },
  { id: "mobile", label: "iPhone / Android", icon: Smartphone },
  { id: "pc", label: "Windows / Mac", icon: Monitor },
  { id: "router", label: "Router", icon: Wifi },
];

const setupGuides: Record<string, Array<{ step: number; title: string; description: string }>> = {
  firestick: [
    { step: 1, title: "Install Downloader App", description: "Go to the Firestick home screen, search for 'Downloader' and install it. Enable 'Apps from Unknown Sources' in Settings > Device > Developer Options." },
    { step: 2, title: "Download IPTV Smarters", description: "Open Downloader and enter: https://enktel.tv/smarters — this will download the IPTV Smarters APK. Install it." },
    { step: 3, title: "Add Your Playlist", description: "Open IPTV Smarters. Select 'Add User'. Enter your name, then paste your M3U URL and EPG URL from above. Tap 'Add User'." },
    { step: 4, title: "Enjoy!", description: "Your channels will load automatically. Browse by category, set up favourites, and enjoy 4K streaming." },
  ],
  "smart-tv": [
    { step: 1, title: "Install Smart IPTV App", description: "On Samsung or LG Smart TVs, go to the App Store and search for 'Smart IPTV' or 'SS IPTV'. Install the app." },
    { step: 2, title: "Note Your MAC Address", description: "Open the app to find your TV's MAC address on the welcome screen. You'll need this for activation." },
    { step: 3, title: "Load Your Playlist", description: "Visit our portal and enter your MAC address along with your M3U URL. The playlist will load on your TV." },
    { step: 4, title: "Start Watching", description: "Restart the app on your TV. All channels will appear organised by category." },
  ],
  mag: [
    { step: 1, title: "Connect MAG Box", description: "Connect your MAG box to your TV via HDMI and to your router via Ethernet (recommended) or WiFi." },
    { step: 2, title: "Get MAC Address", description: "Go to Settings > System Information to find your MAG box's MAC address." },
    { step: 3, title: "Configure Portal", description: "In Settings > Servers > Portals, enter our server URL: http://enktel.tv/portal as Portal 1." },
    { step: 4, title: "Reboot and Enjoy", description: "Reboot your MAG box. It will connect to our server and load your subscription automatically." },
  ],
  mobile: [
    { step: 1, title: "Download IPTV App", description: "iPhone: Download 'GSE Smart IPTV' from the App Store. Android: Download 'IPTV Smarters Pro' or 'TiviMate' from Google Play." },
    { step: 2, title: "Add M3U Playlist", description: "Open the app and look for 'Add Playlist' or 'Add M3U URL'. Paste your M3U URL from above." },
    { step: 3, title: "Add EPG (Optional)", description: "For programme guides, add your EPG URL in the app settings under EPG / XMLTV." },
    { step: 4, title: "Browse Channels", description: "Your channels will load. Browse live TV, VOD, and use the programme guide." },
  ],
  pc: [
    { step: 1, title: "Download VLC Media Player", description: "Download and install VLC from https://videolan.org (free, works on Windows and Mac)." },
    { step: 2, title: "Open Network Stream", description: "In VLC, go to Media > Open Network Stream (Ctrl+N on Windows, Cmd+N on Mac)." },
    { step: 3, title: "Enter Your M3U URL", description: "Paste your M3U URL into the Network URL field and click Play. All channels will load." },
    { step: 4, title: "Advanced: TiviMate for PC", description: "For a better experience on Android emulators, install BlueStacks and then TiviMate for a full TV guide experience on PC." },
  ],
  router: [
    { step: 1, title: "Check Router Compatibility", description: "This method works with routers running DD-WRT, OpenWRT, or Tomato firmware. Check if your router is compatible." },
    { step: 2, title: "Configure DNS", description: "In your router's DNS settings, add our server details (contact support for router-specific credentials)." },
    { step: 3, title: "Install on Devices", description: "Once configured, any device on your network can stream IPTV without needing to enter credentials individually." },
    { step: 4, title: "Contact Support", description: "For router-level setup assistance, contact our support team with your router model for custom instructions." },
  ],
};

export default function DashboardPage() {
  const [activeTab, setActiveTab] = useState("firestick");
  const sub = MOCK_SUBSCRIPTION;

  const daysLeft = Math.max(0, Math.floor((new Date(sub.endDate).getTime() - Date.now()) / 86400000));

  return (
    <div className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-10">
      <h1 className="text-3xl font-bold text-white mb-8">
        My{" "}
        <span className="bg-gradient-to-r from-brand-primary to-brand-secondary bg-clip-text text-transparent">
          Dashboard
        </span>
      </h1>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 mb-8">
        {/* Subscription Status */}
        <div className="lg:col-span-2 bg-brand-card border border-brand-border rounded-xl p-6">
          <div className="flex items-start justify-between mb-5">
            <div>
              <h2 className="text-white font-bold text-xl">{sub.plan} Plan</h2>
              <p className="text-brand-muted text-sm">{sub.id}</p>
            </div>
            <span className="bg-green-500/20 text-green-400 border border-green-500/30 text-xs font-bold px-3 py-1.5 rounded-full">
              ACTIVE
            </span>
          </div>

          {daysLeft <= 14 && (
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
              <Link href="/checkout" className="shrink-0">
                <Button size="sm">Renew Now</Button>
              </Link>
            </div>
          )}

          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 mb-5">
            {[
              { label: "Status", value: "Active" },
              { label: "Days Remaining", value: daysLeft.toString() },
              { label: "Connections", value: `${sub.usedConnections}/${sub.connections}` },
              { label: "Renewal", value: new Date(sub.endDate).toLocaleDateString("en-GB") },
            ].map((item) => (
              <div key={item.label} className="bg-brand-bg border border-brand-border rounded-lg p-3">
                <p className="text-brand-muted text-xs mb-1">{item.label}</p>
                <p className="text-white font-semibold text-sm">{item.value}</p>
              </div>
            ))}
          </div>

          <div className="space-y-3">
            <CopyableUrl label="M3U Playlist URL" url={sub.m3uUrl} />
            <CopyableUrl label="EPG URL (XMLTV)" url={sub.epgUrl} />
          </div>
        </div>

        {/* Quick Stats */}
        <div className="space-y-4">
          <div className="bg-brand-card border border-brand-border rounded-xl p-5">
            <h3 className="text-white font-semibold mb-3">Connection Usage</h3>
            <div className="mb-2">
              <div className="h-2 bg-brand-border rounded-full overflow-hidden">
                <div
                  className="h-full bg-gradient-to-r from-brand-primary to-brand-secondary rounded-full"
                  style={{ width: `${(sub.usedConnections / sub.connections) * 100}%` }}
                />
              </div>
            </div>
            <p className="text-brand-muted text-xs">{sub.usedConnections} of {sub.connections} connections in use</p>
          </div>

          <div className="bg-brand-card border border-brand-border rounded-xl p-5">
            <h3 className="text-white font-semibold mb-3">Subscription Period</h3>
            <div className="mb-2">
              <div className="h-2 bg-brand-border rounded-full overflow-hidden">
                <div
                  className="h-full bg-gradient-to-r from-brand-secondary to-brand-primary rounded-full"
                  style={{ width: `${Math.max(5, (daysLeft / 365) * 100)}%` }}
                />
              </div>
            </div>
            <p className="text-brand-muted text-xs">{daysLeft} days remaining</p>
          </div>

          <a href="/pricing" className="flex items-center justify-between bg-brand-primary/10 border border-brand-primary/30 rounded-xl p-4 hover:bg-brand-primary/20 transition-colors group">
            <div>
              <p className="text-white font-semibold text-sm">Upgrade Plan</p>
              <p className="text-brand-muted text-xs">Get more connections & features</p>
            </div>
            <ChevronRight className="w-4 h-4 text-brand-primary group-hover:translate-x-1 transition-transform" />
          </a>
        </div>
      </div>

      {/* Setup Guides */}
      <div className="bg-brand-card border border-brand-border rounded-xl p-6">
        <h2 className="text-white font-bold text-xl mb-5">Device Setup Guides</h2>

        {/* Tabs */}
        <div className="flex items-center gap-2 overflow-x-auto pb-2 mb-6 scrollbar-thin">
          {setupTabs.map((tab) => (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              className={`flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium whitespace-nowrap transition-colors shrink-0 ${
                activeTab === tab.id
                  ? "bg-brand-primary text-white"
                  : "bg-brand-bg border border-brand-border text-brand-muted hover:text-white hover:border-brand-primary/40"
              }`}
            >
              <tab.icon className="w-4 h-4" />
              {tab.label}
            </button>
          ))}
        </div>

        {/* Steps */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          {(setupGuides[activeTab] || []).map((step) => (
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
      </div>
    </div>
  );
}
