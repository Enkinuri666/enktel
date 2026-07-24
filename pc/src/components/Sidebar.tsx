import { NavLink } from 'react-router-dom';
import {
  Home, Tv, Film, Popcorn, Trophy, CalendarDays,
  Search, Bookmark, Radio, Settings,
} from 'lucide-react';

const NAV = [
  { to: '/', icon: Home, label: 'Home', end: true },
  { to: '/live', icon: Tv, label: 'Live TV' },
  { to: '/guide', icon: CalendarDays, label: 'TV Guide' },
  { to: '/movies', icon: Film, label: 'Movies' },
  { to: '/series', icon: Popcorn, label: 'Series' },
  { to: '/sports', icon: Trophy, label: 'Sports' },
  { to: '/search', icon: Search, label: 'Search' },
  { to: '/watchlist', icon: Bookmark, label: 'Watchlist' },
  { to: '/recordings', icon: Radio, label: 'Recordings' },
];

/**
 * Left navigation rail. Collapsed by default (icons only) so the content area
 * gets all the width; expands on hover.
 */
export default function Sidebar() {
  return (
    <aside className="group shrink-0 w-16 hover:w-56 transition-[width] duration-200 bg-surface/70 backdrop-blur-md border-r border-white/5 flex flex-col">
      <nav className="flex-1 py-3 flex flex-col gap-1 px-2">
        {NAV.map(({ to, icon: Icon, label, end }) => (
          <NavLink
            key={to}
            to={to}
            end={end}
            className={({ isActive }) =>
              `flex items-center gap-3 px-3 py-2.5 rounded-md focus-ring ` +
              `${isActive
                ? 'bg-brand/25 text-white border border-brand/60'
                : 'text-textDim hover:text-white hover:bg-white/5'}`
            }
          >
            <Icon className="h-5 w-5 shrink-0" />
            <span className="text-sm font-semibold opacity-0 group-hover:opacity-100 transition-opacity whitespace-nowrap">
              {label}
            </span>
          </NavLink>
        ))}
      </nav>
      <NavLink
        to="/settings"
        className={({ isActive }) =>
          `flex items-center gap-3 px-3 py-2.5 mx-2 mb-3 rounded-md focus-ring ` +
          `${isActive
            ? 'bg-brand/25 text-white border border-brand/60'
            : 'text-textDim hover:text-white hover:bg-white/5'}`
        }
      >
        <Settings className="h-5 w-5 shrink-0" />
        <span className="text-sm font-semibold opacity-0 group-hover:opacity-100 transition-opacity whitespace-nowrap">
          Settings
        </span>
      </NavLink>
    </aside>
  );
}
