import { useState } from 'react';
import { Route, Routes, Navigate } from 'react-router-dom';
import EnktelSplash from './components/EnktelSplash';
import TitleBar from './components/TitleBar';
import Sidebar from './components/Sidebar';
import CommandPalette from './components/CommandPalette';
import HomePage from './pages/HomePage';
import LiveTVPage from './pages/LiveTVPage';
import MoviesPage from './pages/MoviesPage';
import SeriesPage from './pages/SeriesPage';
import SportsPage from './pages/SportsPage';
import GuidePage from './pages/GuidePage';
import SearchPage from './pages/SearchPage';
import WatchlistPage from './pages/WatchlistPage';
import RecordingsPage from './pages/RecordingsPage';
import PhonePage from './pages/PhonePage';
import SettingsPage from './pages/SettingsPage';
import OnboardingPage from './pages/OnboardingPage';
import { useSettings } from './stores/settings';

/**
 * App shell:
 *  ┌──────────────────────────── TitleBar ────────────────────────────┐
 *  │  ● ENKTEL IPTV                            search · voice · —  ▢ ✕│
 *  ├──────┬───────────────────────────────────────────────────────────┤
 *  │      │                                                           │
 *  │ Nav  │              Page content (routed)                        │
 *  │      │                                                           │
 *  └──────┴───────────────────────────────────────────────────────────┘
 *
 * Cold-launch always shows the branded splash for ~1.6s; the router mounts
 * behind it and starts pre-fetching data so the app is warm the moment the
 * splash fades. If the user has no profile configured, Onboarding takes over
 * before the main shell.
 */
export default function App() {
  const [splashDone, setSplashDone] = useState(false);
  const [paletteOpen, setPaletteOpen] = useState(false);
  const hasProfile = useSettings((s) => s.profile != null);

  return (
    <div className="h-screen w-screen flex flex-col overflow-hidden">
      <TitleBar onCommandPalette={() => setPaletteOpen(true)} />
      <div className="flex-1 flex overflow-hidden">
        {hasProfile && <Sidebar />}
        <main className="flex-1 overflow-y-auto overflow-x-hidden relative">
          <Routes>
            <Route
              path="/"
              element={hasProfile ? <HomePage /> : <Navigate to="/onboarding" replace />}
            />
            <Route path="/onboarding" element={<OnboardingPage />} />
            <Route path="/live/:channelKey?" element={<LiveTVPage />} />
            <Route path="/movies" element={<MoviesPage />} />
            <Route path="/series" element={<SeriesPage />} />
            <Route path="/sports" element={<SportsPage />} />
            <Route path="/guide" element={<GuidePage />} />
            <Route path="/search" element={<SearchPage />} />
            <Route path="/watchlist" element={<WatchlistPage />} />
            <Route path="/recordings" element={<RecordingsPage />} />
            <Route path="/devices" element={<PhonePage />} />
            <Route path="/settings/*" element={<SettingsPage />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </main>
      </div>
      <CommandPalette open={paletteOpen} onClose={() => setPaletteOpen(false)} />
      {!splashDone && <EnktelSplash onDone={() => setSplashDone(true)} />}
    </div>
  );
}
