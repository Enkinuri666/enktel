import type { Metadata } from "next";
import { Tv, Smartphone, MonitorPlay, Box } from "lucide-react";
import Card from "@/components/ui/Card";

export const metadata: Metadata = { title: "Setup Guides" };

const guides = [
  {
    icon: Tv,
    device: "Smart TV (Samsung, LG, Sony)",
    app: "Smarters Player Lite / IPTV Smarters",
    steps: [
      "Download \"Smarters Player Lite\" from your TV's app store.",
      "Open the app and choose \"Login with Xtream Codes API\".",
      "Enter your dashboard username, password, and server URL.",
      "Save and start watching — channels load automatically.",
    ],
  },
  {
    icon: Box,
    device: "Amazon Firestick / Fire TV",
    app: "TiviMate or IPTV Smarters Pro",
    steps: [
      "Search for \"TiviMate\" in the Amazon App Store and install it.",
      "Open TiviMate, select \"Add Playlist\" → \"M3U Playlist\".",
      "Paste your M3U Playlist URL from your dashboard.",
      "Add your EPG URL when prompted for full program guide data.",
    ],
  },
  {
    icon: Smartphone,
    device: "Android / iOS Phones & Tablets",
    app: "IPTV Smarters Pro",
    steps: [
      "Install \"IPTV Smarters Pro\" from the Play Store or App Store.",
      "Tap \"Login with Xtream Codes API\".",
      "Enter the username, password, and server URL from your dashboard.",
      "Tap Login — your channels and VOD library will sync.",
    ],
  },
  {
    icon: MonitorPlay,
    device: "Windows / Mac / VLC",
    app: "VLC Media Player",
    steps: [
      "Open VLC → Media → Open Network Stream.",
      "Paste your M3U Playlist URL from your dashboard.",
      "Click Play — VLC will load the full channel list.",
      "For a richer guide, use the IPTV Smarters desktop app instead of VLC.",
    ],
  },
];

export default function SetupGuidesPage() {
  return (
    <div className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
      <div className="text-center mb-10">
        <h1 className="text-3xl sm:text-4xl font-black text-white mb-3">Setup Guides</h1>
        <p className="text-brand-muted max-w-xl mx-auto">
          Find your device below for step-by-step setup instructions. Your credentials and URLs are always
          available in your{" "}
          <a href="/dashboard" className="text-brand-primary hover:underline">dashboard</a>.
        </p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {guides.map((guide) => {
          const Icon = guide.icon;
          return (
            <Card key={guide.device} className="p-6">
              <div className="flex items-center gap-3 mb-4">
                <div className="w-10 h-10 bg-brand-primary/10 border border-brand-primary/30 rounded-lg flex items-center justify-center shrink-0">
                  <Icon className="w-5 h-5 text-brand-primary" />
                </div>
                <div>
                  <h2 className="text-white font-bold">{guide.device}</h2>
                  <p className="text-brand-muted text-xs">{guide.app}</p>
                </div>
              </div>
              <ol className="space-y-2">
                {guide.steps.map((step, i) => (
                  <li key={i} className="flex gap-3 text-sm text-brand-muted">
                    <span className="shrink-0 w-5 h-5 rounded-full bg-white/5 border border-brand-border text-white text-xs flex items-center justify-center font-semibold">
                      {i + 1}
                    </span>
                    {step}
                  </li>
                ))}
              </ol>
            </Card>
          );
        })}
      </div>

      <div className="mt-10 text-center">
        <p className="text-brand-muted text-sm">
          Still stuck? <a href="/contact" className="text-brand-primary hover:underline">Contact support</a> and we'll help you get streaming.
        </p>
      </div>
    </div>
  );
}
