import { Tv, Smartphone, Monitor, Wifi, Box } from "lucide-react";
import type { LucideIcon } from "lucide-react";

export type DeviceId = "firestick" | "smart-tv" | "mag" | "mobile" | "pc" | "router";

export interface DeviceGuideStep {
  step: number;
  title: string;
  description: string;
}

export interface DeviceGuide {
  id: DeviceId;
  label: string;
  icon: LucideIcon;
  app: string;
  steps: DeviceGuideStep[];
}

export const DEVICE_GUIDES: DeviceGuide[] = [
  {
    id: "firestick",
    label: "Firestick",
    icon: Tv,
    app: "TiviMate or IPTV Smarters Pro",
    steps: [
      { step: 1, title: "Install Downloader App", description: "Go to the Firestick home screen, search for 'Downloader' and install it. Enable 'Apps from Unknown Sources' in Settings > Device > Developer Options." },
      { step: 2, title: "Download IPTV Smarters", description: "Open Downloader and enter: https://enktel.tv/smarters — this will download the IPTV Smarters APK. Install it." },
      { step: 3, title: "Add Your Playlist", description: "Open IPTV Smarters. Select 'Add User'. Enter your name, then paste your M3U URL and EPG URL from your dashboard. Tap 'Add User'." },
      { step: 4, title: "Enjoy!", description: "Your channels will load automatically. Browse by category, set up favourites, and enjoy 4K streaming." },
    ],
  },
  {
    id: "smart-tv",
    label: "Smart TV",
    icon: Tv,
    app: "Smart IPTV / SS IPTV",
    steps: [
      { step: 1, title: "Install Smart IPTV App", description: "On Samsung or LG Smart TVs, go to the App Store and search for 'Smart IPTV' or 'SS IPTV'. Install the app." },
      { step: 2, title: "Note Your MAC Address", description: "Open the app to find your TV's MAC address on the welcome screen. You'll need this for activation." },
      { step: 3, title: "Load Your Playlist", description: "Visit our portal and enter your MAC address along with your M3U URL from your dashboard. The playlist will load on your TV." },
      { step: 4, title: "Start Watching", description: "Restart the app on your TV. All channels will appear organised by category." },
    ],
  },
  {
    id: "mag",
    label: "MAG Box",
    icon: Box,
    app: "Built-in Portal",
    steps: [
      { step: 1, title: "Connect MAG Box", description: "Connect your MAG box to your TV via HDMI and to your router via Ethernet (recommended) or WiFi." },
      { step: 2, title: "Get MAC Address", description: "Go to Settings > System Information to find your MAG box's MAC address." },
      { step: 3, title: "Configure Portal", description: "In Settings > Servers > Portals, enter the server URL shown in your dashboard as Portal 1." },
      { step: 4, title: "Reboot and Enjoy", description: "Reboot your MAG box. It will connect to our server and load your subscription automatically." },
    ],
  },
  {
    id: "mobile",
    label: "iPhone / Android",
    icon: Smartphone,
    app: "IPTV Smarters Pro / GSE Smart IPTV",
    steps: [
      { step: 1, title: "Download IPTV App", description: "iPhone: Download 'GSE Smart IPTV' from the App Store. Android: Download 'IPTV Smarters Pro' or 'TiviMate' from Google Play." },
      { step: 2, title: "Add M3U Playlist", description: "Open the app and look for 'Add Playlist' or 'Add M3U URL'. Paste your M3U URL from your dashboard." },
      { step: 3, title: "Add EPG (Optional)", description: "For programme guides, add your EPG URL in the app settings under EPG / XMLTV." },
      { step: 4, title: "Browse Channels", description: "Your channels will load. Browse live TV, VOD, and use the programme guide." },
    ],
  },
  {
    id: "pc",
    label: "Windows / Mac",
    icon: Monitor,
    app: "VLC Media Player",
    steps: [
      { step: 1, title: "Download VLC Media Player", description: "Download and install VLC from https://videolan.org (free, works on Windows and Mac)." },
      { step: 2, title: "Open Network Stream", description: "In VLC, go to Media > Open Network Stream (Ctrl+N on Windows, Cmd+N on Mac)." },
      { step: 3, title: "Enter Your M3U URL", description: "Paste your M3U URL from your dashboard into the Network URL field and click Play. All channels will load." },
      { step: 4, title: "Advanced: TiviMate for PC", description: "For a better experience, install BlueStacks and then TiviMate for a full TV guide experience on PC." },
    ],
  },
  {
    id: "router",
    label: "Router",
    icon: Wifi,
    app: "DD-WRT / OpenWRT / Tomato",
    steps: [
      { step: 1, title: "Check Router Compatibility", description: "This method works with routers running DD-WRT, OpenWRT, or Tomato firmware. Check if your router is compatible." },
      { step: 2, title: "Configure DNS", description: "In your router's DNS settings, add our server details (contact support for router-specific credentials)." },
      { step: 3, title: "Install on Devices", description: "Once configured, any device on your network can stream IPTV without needing to enter credentials individually." },
      { step: 4, title: "Contact Support", description: "For router-level setup assistance, contact our support team with your router model for custom instructions." },
    ],
  },
];

export function getDeviceGuide(id?: string | null): DeviceGuide | undefined {
  return DEVICE_GUIDES.find((d) => d.id === id);
}
