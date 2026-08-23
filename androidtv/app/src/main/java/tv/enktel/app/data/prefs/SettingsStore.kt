package tv.enktel.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("enktel_settings")

class SettingsStore(private val context: Context) {
    private val ACTIVE_PROFILE = longPreferencesKey("active_profile")
    private val STREAM_FORMAT = stringPreferencesKey("stream_format")
    private val BUFFER_PROFILE = stringPreferencesKey("buffer_profile")
    private val LAST_CHANNEL = stringPreferencesKey("last_channel")
    private val EPG_LAST_SYNC = longPreferencesKey("epg_last_sync")
    private val AUTOPLAY_LAST = booleanPreferencesKey("autoplay_last")
    private val GUIDE_HOURS = intPreferencesKey("guide_hours")

    // v1.0.3 additions
    private val PARENTAL_PIN = stringPreferencesKey("parental_pin_hash")
    private val LOCKED_CATEGORIES = stringSetPreferencesKey("locked_categories")
    private val EPG_OFFSET_MIN = intPreferencesKey("epg_offset_min")
    private val SUB_SCALE = intPreferencesKey("sub_scale_pct")
    private val SUB_STYLE = stringPreferencesKey("sub_style")
    private val REC_PREFIX_MIN = intPreferencesKey("rec_prefix_min")
    private val REC_SUFFIX_MIN = intPreferencesKey("rec_suffix_min")
    private val AUTO_EPG_HOURS = intPreferencesKey("auto_epg_hours")
    private val HIDDEN_CHANNELS = stringSetPreferencesKey("hidden_channels")
    private val VOD_SORT = stringPreferencesKey("vod_sort")
    private val RECENT_CHANNELS = stringPreferencesKey("recent_channels")
    private val LIVE_SHIFT_ENABLED = booleanPreferencesKey("live_shift_enabled")

    // v1.2.0 additions
    private val LOUDNESS_ON = booleanPreferencesKey("loudness_on")
    private val SUB_COLOR = stringPreferencesKey("sub_color") // white | yellow | cyan | green
    private val SUB_EDGE = stringPreferencesKey("sub_edge") // none | outline | shadow | depressed | raised
    private val SUB_BG_ALPHA = intPreferencesKey("sub_bg_alpha")
    private val EXT_SUB_URL = stringPreferencesKey("ext_sub_url")
    private val AUTOPLAY_NEXT_EP = booleanPreferencesKey("autoplay_next_ep")
    private val TUNNELING = booleanPreferencesKey("tunneled_playback")

    /**
     * Relay playback: fetch streams through our own origin instead of opening
     * the stream host directly.
     *
     * Off by default — direct is fewer hops and lower latency, and is right
     * whenever it works. Relay is the answer when the path between this device
     * and the stream host is what is broken.
     */
    private val RELAY_PLAYBACK = booleanPreferencesKey("relay_playback")

    /**
     * A relay endpoint of the viewer's own, replacing the built-in one.
     *
     * The built-in relay runs where this project can deploy, which is not
     * everywhere. A channel geo-locked to a country with no serverless region —
     * Croatia is the case that forced this — is unreachable through it no
     * matter how many regions are added, because none of them is *that*
     * country. The viewer can be in a position to fix that when we are not: a
     * small host inside the right country, running the same `/api/stream`
     * route or any prefix relay that takes `?u=`, and this pointed at it.
     *
     * Blank means the built-in one.
     */
    private val RELAY_BASE = stringPreferencesKey("relay_base")

    /**
     * A proxy to reach hosts that refuse this device, e.g. `socks5://1.2.3.4:1080`.
     *
     * Distinct from the relay above, and much easier to come by: a relay is a
     * service someone has to deploy, where a proxy is a port. Both answer the
     * same question — which country the request appears to come from — and a
     * channel published only inside one country needs one or the other.
     */
    private val PROXY_ENDPOINT = stringPreferencesKey("proxy_endpoint")
    private val PROXY_USER = stringPreferencesKey("proxy_user")
    private val PROXY_PASS = stringPreferencesKey("proxy_pass")
    private val CAPTION_MODE = stringPreferencesKey("caption_mode") // off | auto | en | hr
    private val SKIP_INTRO_SEC = intPreferencesKey("skip_intro_sec")
    private val PIP_ENABLED = booleanPreferencesKey("pip_enabled")
    private val AUTO_PIP_ON_BACK = booleanPreferencesKey("auto_pip_on_back")
    private val AUTO_PIP_ON_HOME = booleanPreferencesKey("auto_pip_on_home")
    private val SCREENSAVER_MIN = intPreferencesKey("screensaver_min")
    private val FIRST_RUN_DONE = booleanPreferencesKey("first_run_done")
    private val SCORES_ENABLED = booleanPreferencesKey("scores_enabled")
    private val HIDDEN_ITEMS = stringSetPreferencesKey("hidden_items")

    // v1.4.0 additions
    private val THEME = stringPreferencesKey("theme")
    private val UI_OPACITY_PCT = intPreferencesKey("ui_opacity_pct") // 60-100
    private val TEXT_SCALE_PCT = intPreferencesKey("text_scale_pct") // 85-140
    private val START_ON_BOOT = booleanPreferencesKey("start_on_boot")
    private val BACK_ACTION = stringPreferencesKey("back_action") // exit | pip | dock
    // v1.38.0 docked playback: where the mini window sits and how big it is.
    private val DOCK_CORNER = stringPreferencesKey("dock_corner")
    // v1.38.1: optional TheSportsDB Patreon key. Blank = the public test key,
    // which works for schedules and highlights but not in-play scores.
    private val SPORTSDB_KEY = stringPreferencesKey("sportsdb_key")
    private val DOCK_SIZE_STEP = intPreferencesKey("dock_size_step") // 0 small | 1 medium | 2 large
    private val WAKE_WORD_ENABLED = booleanPreferencesKey("wake_word_enabled")
    // v1.13.0: newline-separated `host[:port]` list of backup gateways used by
    // StreamHealthInterceptor when the primary throws 403 / times out.
    private val BACKUP_GATEWAYS = stringPreferencesKey("backup_gateways")
    // v1.14.2: Discord webhook URL for social presence pushes.  Blank disables.
    private val DISCORD_WEBHOOK = stringPreferencesKey("discord_webhook")
    // v1.26.0 — voice channel name appended to the manual "Share to Discord"
    // announce, so the message reads "🎬 Now streaming X in $DISCORD_VOICE_CHANNEL".
    private val DISCORD_VOICE_CHANNEL = stringPreferencesKey("discord_voice_channel")
    // v1.26.0 — "Streaming Companion Mode" preset: bumps min buffer to a
    // large floor, locks video to the highest steady bitrate, and disables
    // aggressive upshifts. Used when broadcasting playback out over Discord
    // screen-share (bitrate flapping looks terrible on the receiving end).
    private val COMPANION_MODE = booleanPreferencesKey("companion_mode")
    // v1.15.0: master toggle for the app's sonic-branding earcons
    // (nav clicks, rail-end chimes, movie-open swell).  Voice-command
    // earcons are separately gated by the voice feature itself.
    private val UI_SOUNDS_ENABLED = booleanPreferencesKey("ui_sounds_enabled")
    // v1.18.0: Kids Mode — PIN-gated simplified UI restricted to family-safe
    // content.  Reuses the existing parental PIN (no separate credential).
    private val KIDS_MODE_ENABLED = booleanPreferencesKey("kids_mode_enabled")

    // v1.19.0: player HUD auto-hide duration in seconds.
    //   0  = never auto-hide (stays until user explicitly dismisses)
    //   1+ = seconds before the info bar / controls fade out
    // Applies to both the live player's info overlay and the VOD player's
    // controls. Default 8s — longer than the previous hardcoded 5-6s so
    // users on a couch with a remote have time to read the current channel /
    // program info before it vanishes.
    private val HUD_AUTOHIDE_SEC = intPreferencesKey("hud_autohide_sec")

    // v1.19.1: force ExoPlayer to treat every VOD stream strictly as MP4,
    // bypassing container sniffing. Useful when a panel serves a container
    // that ExoPlayer detects as ambiguous (MKV/MP4/TS) and the wrong
    // extractor gets picked. Off by default — the permissive extractor
    // chain shipped in v1.18.3 handles most mismatches transparently.
    private val VOD_FORCE_MP4 = booleanPreferencesKey("vod_force_mp4")
    private val CUSTOM_UA = stringPreferencesKey("custom_user_agent")
    private val WELCOME_SEEN = booleanPreferencesKey("welcome_splash_seen")

    // v1.20.0: TMDB API key for the metadata enrichment worker. v3 numeric
    // key OR v4 read-only bearer token accepted. Blank = worker no-ops.
    // Instructions in Settings help text.
    private val TMDB_API_KEY = stringPreferencesKey("tmdb_api_key")

    // v1.21.0 power-user playback controls.
    // Decoding mode: "hwplus" (default — the SoC's decoders answer first and
    // the bundled FFmpeg extension sits behind them as a fallback, Media3's
    // EXTENSION_RENDERER_MODE_ON), "hw" (extensions off entirely), "sw"
    // (FFmpeg ahead of the platform, EXTENSION_RENDERER_MODE_PREFER).
    //
    // Live again as of v1.53.0: app/libs carries a prebuilt FFmpeg audio
    // decoder, and it is what makes AC-3, E-AC-3, DTS and TrueHD decode on
    // hardware that cannot. It was inert between v1.51.0 and v1.52.0, when no
    // extension renderer was on the classpath at all.
    //
    // "hwplus" mapped to PREFER until it was found to be handing FFmpeg every
    // codec it claims — Opus and AAC among them — which is what stuttered an
    // HEVC + Opus title on a Fire TV Stick. See player/AudioDecoding.
    //
    // Stored values from older builds still read correctly: "hwplus" and the
    // legacy "on" both resolve to the new default, "hw" is unchanged, and
    // nothing ever wrote "sw".
    private val DECODER_MODE = stringPreferencesKey("decoder_mode")
    // Manual override for the ExoPlayer LoadControl minimum buffer, in ms.
    // 0 = keep the profile default (Low / Balanced / Large / Auto). Any
    // positive value overrides just the minimum-buffer field so a user on
    // a jittery ISP can force a 5s buffer floor without moving to Large.
    private val MIN_BUFFER_MS = intPreferencesKey("min_buffer_ms")
    // Dialogue boost — DynamicsProcessing tuned for the 200-3400 Hz voice
    // band. Levels: "off" | "low" | "medium" | "high".
    private val DIALOGUE_BOOST = stringPreferencesKey("dialogue_boost")
    // Background audio: when true, live/vod playback continues after the
    // screen turns off, so a user can leave a news/podcast/sport feed
    // running in the background.
    private val BACKGROUND_AUDIO = booleanPreferencesKey("background_audio")

    // v1.50.0 — split buffer profiles for VOD vs Live IPTV. VOD prioritises
    // stability (large buffer), Live prioritises latency (small buffer, fast
    // channel zap). "auto" picks best-practice defaults per stream type.
    private val VOD_BUFFER_PROFILE = stringPreferencesKey("vod_buffer_profile")
    private val LIVE_BUFFER_PROFILE = stringPreferencesKey("live_buffer_profile")
    private val VOD_MIN_BUFFER_MS = intPreferencesKey("vod_min_buffer_ms")
    private val VOD_MAX_BUFFER_MS = intPreferencesKey("vod_max_buffer_ms")
    private val VOD_PLAYBACK_MS = intPreferencesKey("vod_playback_ms")
    private val VOD_REBUFFER_MS = intPreferencesKey("vod_rebuffer_ms")
    private val LIVE_MIN_BUFFER_MS = intPreferencesKey("live_min_buffer_ms")
    private val LIVE_MAX_BUFFER_MS = intPreferencesKey("live_max_buffer_ms")
    private val LIVE_PLAYBACK_MS = intPreferencesKey("live_playback_ms")
    private val LIVE_REBUFFER_MS = intPreferencesKey("live_rebuffer_ms")
    // Memory allocator chunk size in KB. 0 = default (16 KB). Higher
    // values (e.g. 2048 for 2 MB) help with 4K and large MKV buffering.
    private val ALLOCATOR_SIZE_KB = intPreferencesKey("allocator_size_kb")
    // v1.50.0 — trial-blocked flag. True when the device's free trial
    // has been used and expired. Prevents further trial creations.
    private val TRIAL_USED = booleanPreferencesKey("trial_used")
    private val TRIAL_EXPIRES_AT = longPreferencesKey("trial_expires_at")

    // v1.22.0 download-manager overhaul.
    //   downloadEngine: "auto" (parallel when the source + target permit,
    //                   else falls back to system), "parallel" (force the
    //                   new 4-way ranged OkHttp downloader), "system"
    //                   (platform DownloadManager — best OS notification
    //                   integration, single-stream).
    //   downloadFolderUri: SAF tree URI the user picked in Settings. Empty
    //                   means "use the app's default (app-scoped external
    //                   Movies dir)".
    private val DOWNLOAD_ENGINE = stringPreferencesKey("download_engine")
    private val DOWNLOAD_FOLDER_URI = stringPreferencesKey("download_folder_uri")

    // v1.34.0 — Netflix-style hover auto-trailers on the Movies/Series grids.
    // Needs a TMDB API key (the trailer id comes from the same enrichment
    // pass); inert without one, so leaving it on costs nothing.
    private val AUTO_TRAILERS = booleanPreferencesKey("auto_trailers")
    // v1.34.0 — Sports: live in-play data, official broadcast schedules and
    // highlights come from TheSportsDB. Off by default (extra network chatter),
    // shares the existing scoresEnabled toggle's spirit but is separate so a
    // user can have live scores without the heavier match-centre polling.
    private val MATCH_CENTER = booleanPreferencesKey("match_center_enabled")

    // v1.36.0 — user-dragged split positions for Live TV Browse mode, stored
    // as a percentage of the axis so they survive rotation and device changes.
    // Landscape and portrait are separate because a good video/dock balance
    // side-by-side is a bad one stacked. The third is the channel-list vs
    // guide split inside the dock itself.
    // v1.37.0 — hold downloads back on a metered connection. Defaults ON for
    // the mobile flavor and OFF for TV: a phone on cellular can silently burn
    // several GB on one film, whereas a set-top box is on Wi-Fi or Ethernet and
    // the restriction would only ever get in the way.
    private val DOWNLOADS_WIFI_ONLY = booleanPreferencesKey("downloads_wifi_only")

    private val BROWSE_SPLIT_LAND = intPreferencesKey("browse_split_land_pct")
    private val BROWSE_SPLIT_PORT = intPreferencesKey("browse_split_port_pct")
    private val DOCK_SPLIT = intPreferencesKey("dock_split_pct")

    // v1.11.0: content organisation. Each kind holds:
    //  - `<kind>_category_order` — pipe-separated categoryIds in the user's chosen order
    //  - `<kind>_hidden_categories` — set of categoryIds the user has hidden
    private val LIVE_CAT_ORDER = stringPreferencesKey("live_cat_order")
    private val LIVE_HIDDEN_CATS = stringSetPreferencesKey("live_hidden_cats")
    private val VOD_CAT_ORDER = stringPreferencesKey("vod_cat_order")
    private val VOD_HIDDEN_CATS = stringSetPreferencesKey("vod_hidden_cats")
    private val SERIES_CAT_ORDER = stringPreferencesKey("series_cat_order")
    private val SERIES_HIDDEN_CATS = stringSetPreferencesKey("series_hidden_cats")

    val activeProfileId: Flow<Long> = context.dataStore.data.map { it[ACTIVE_PROFILE] ?: 0L }
    val streamFormat: Flow<String> = context.dataStore.data.map { it[STREAM_FORMAT] ?: "hls" }
    val bufferProfile: Flow<String> = context.dataStore.data.map { it[BUFFER_PROFILE] ?: "balanced" }
    val lastChannel: Flow<String> = context.dataStore.data.map { it[LAST_CHANNEL] ?: "" }
    val autoplayLast: Flow<Boolean> = context.dataStore.data.map { it[AUTOPLAY_LAST] ?: true }

    val parentalPinHash: Flow<String> = context.dataStore.data.map { it[PARENTAL_PIN] ?: "" }
    val lockedCategories: Flow<Set<String>> = context.dataStore.data.map { it[LOCKED_CATEGORIES] ?: emptySet() }
    val epgOffsetMin: Flow<Int> = context.dataStore.data.map { it[EPG_OFFSET_MIN] ?: 0 }
    val subScalePct: Flow<Int> = context.dataStore.data.map { it[SUB_SCALE] ?: 100 }
    val subStyle: Flow<String> = context.dataStore.data.map { it[SUB_STYLE] ?: "default" } // default|outlined|shadow|box
    val recPrefixMin: Flow<Int> = context.dataStore.data.map { it[REC_PREFIX_MIN] ?: 2 }
    val recSuffixMin: Flow<Int> = context.dataStore.data.map { it[REC_SUFFIX_MIN] ?: 5 }
    val autoEpgHours: Flow<Int> = context.dataStore.data.map { it[AUTO_EPG_HOURS] ?: 12 }
    val hiddenChannels: Flow<Set<String>> = context.dataStore.data.map { it[HIDDEN_CHANNELS] ?: emptySet() }
    /**
     * How Movies and Series are ordered until the user says otherwise.
     *
     * Was "name". On a catalogue of 200,000 films that means the app opened,
     * every single time, on whatever begins with a digit or the letter A — the
     * same screenful for every user on every visit, and no evidence anywhere in
     * it that the library is large or that anything was ever added to it. A
     * tester put it exactly: it seems to show the same old content.
     *
     * "added" opens on what the panel most recently ingested, so the first
     * screen changes as the catalogue does. Anyone who prefers alphabetical
     * still has it — this is a default, not a rule, and a stored choice
     * overrides it.
     */
    val vodSort: Flow<String> = context.dataStore.data.map { it[VOD_SORT] ?: "added" } // name|rating|added|year
    val recentChannels: Flow<List<String>> = context.dataStore.data.map {
        it[RECENT_CHANNELS]?.split('|')?.filter(String::isNotBlank) ?: emptyList()
    }
    val liveShiftEnabled: Flow<Boolean> = context.dataStore.data.map { it[LIVE_SHIFT_ENABLED] ?: true }

    val loudnessOn: Flow<Boolean> = context.dataStore.data.map { it[LOUDNESS_ON] ?: false }
    val subColor: Flow<String> = context.dataStore.data.map { it[SUB_COLOR] ?: "white" }
    val subEdge: Flow<String> = context.dataStore.data.map { it[SUB_EDGE] ?: "outline" }
    val subBgAlpha: Flow<Int> = context.dataStore.data.map { it[SUB_BG_ALPHA] ?: 0 }
    val extSubUrl: Flow<String> = context.dataStore.data.map { it[EXT_SUB_URL] ?: "" }
    val autoplayNextEp: Flow<Boolean> = context.dataStore.data.map { it[AUTOPLAY_NEXT_EP] ?: true }

    /**
     * Tunneled hardware playback, on the television build only.
     *
     * Defaults on, which is what it has always been. Exposed because tunneling
     * is one of exactly two things that differ between the TV and mobile
     * builds, and a title that plays cleanly on a phone and stutters on a Fire
     * TV is most likely tripping over one of them — but which one is a property
     * of the device's media stack, not something the app can ask.
     */
    val tunneling: Flow<Boolean> = context.dataStore.data.map { it[TUNNELING] ?: true }

    /** See [RELAY_PLAYBACK]. Direct playback unless the viewer turns this on. */
    val relayPlayback: Flow<Boolean> = context.dataStore.data.map { it[RELAY_PLAYBACK] ?: false }

    /** The viewer's own relay endpoint, or blank for the built-in one. */
    val relayBase: Flow<String> = context.dataStore.data.map { it[RELAY_BASE].orEmpty() }

    val proxyEndpoint: Flow<String> = context.dataStore.data.map { it[PROXY_ENDPOINT].orEmpty() }
    val proxyUser: Flow<String> = context.dataStore.data.map { it[PROXY_USER].orEmpty() }
    val proxyPass: Flow<String> = context.dataStore.data.map { it[PROXY_PASS].orEmpty() }

    /** The three together, so a collector reconfigures once rather than thrice. */
    val proxyConfig: Flow<Triple<String, String, String>> = context.dataStore.data.map {
        Triple(it[PROXY_ENDPOINT].orEmpty(), it[PROXY_USER].orEmpty(), it[PROXY_PASS].orEmpty())
    }

    /**
     * Closed captions on live TV — off, automatic, English or Croatian.
     *
     * Defaults off, because turning it on changes what the MPEG-TS extractor
     * exposes: on a stream whose PMT does not describe its captions it declares
     * CEA-608 and CEA-708 anyway, rather than extracting nothing. On a channel
     * that genuinely carries neither, that is two empty entries in the track
     * picker. Worth it when asked for, not worth it by default.
     */
    val captionMode: Flow<String> = context.dataStore.data.map {
        it[CAPTION_MODE] ?: tv.enktel.app.player.ClosedCaptions.OFF
    }

    suspend fun setCaptionMode(mode: String) {
        context.dataStore.edit { it[CAPTION_MODE] = mode }
    }
    val skipIntroSec: Flow<Int> = context.dataStore.data.map { it[SKIP_INTRO_SEC] ?: 0 }
    val pipEnabled: Flow<Boolean> = context.dataStore.data.map { it[PIP_ENABLED] ?: true }
    val autoPipOnBack: Flow<Boolean> = context.dataStore.data.map { it[AUTO_PIP_ON_BACK] ?: true }
    val autoPipOnHome: Flow<Boolean> = context.dataStore.data.map { it[AUTO_PIP_ON_HOME] ?: true }
    val screensaverMin: Flow<Int> = context.dataStore.data.map { it[SCREENSAVER_MIN] ?: 5 }
    val firstRunDone: Flow<Boolean> = context.dataStore.data.map { it[FIRST_RUN_DONE] ?: false }
    val scoresEnabled: Flow<Boolean> = context.dataStore.data.map { it[SCORES_ENABLED] ?: false }
    val hiddenItems: Flow<Set<String>> = context.dataStore.data.map { it[HIDDEN_ITEMS] ?: emptySet() }

    // v1.27.0 default flipped to "cinematic" — Midnight Charcoal base with
    // Electric Indigo D-Pad focus + Cyber Cyan live badge, per the TV design
    // brief. Existing installs on Obsidian keep their setting via DataStore.
    // v1.23.0 default flipped to "obsidian" — the new premium theme. Existing
    // installs that picked a palette keep their preference (the ?: only
    // fires when the pref is absent).
    // v1.35.0 default flipped to "deep_space" — the Deep Space & Neon Accent
    // token set. As with every previous default change, the ?: only fires when
    // the pref is absent, so anyone who has picked a theme keeps it.
    // v1.46.0 default is "enktel_neon" — the OLED-native palette. The `?:`
    // only applies when the pref is absent, so anyone who has explicitly
    // picked a theme keeps it across the upgrade.
    val theme: Flow<String> = context.dataStore.data.map { it[THEME] ?: "enktel_neon" }
    val uiOpacityPct: Flow<Int> = context.dataStore.data.map { it[UI_OPACITY_PCT] ?: 92 }
    val textScalePct: Flow<Int> = context.dataStore.data.map { it[TEXT_SCALE_PCT] ?: 100 }
    val startOnBoot: Flow<Boolean> = context.dataStore.data.map { it[START_ON_BOOT] ?: false }
    val backAction: Flow<String> = context.dataStore.data.map {
        // "guide_dock" was the pre-v1.38.0 name, when Back could only navigate
        // to the guide and abandon playback. Docking now keeps the stream in a
        // mini window over any screen, so old preferences map onto the real
        // thing rather than silently reverting to "exit".
        when (val v = it[BACK_ACTION] ?: "exit") {
            "guide_dock" -> "dock"
            else -> v
        }
    }
    val sportsDbKey: Flow<String> = context.dataStore.data.map { it[SPORTSDB_KEY].orEmpty() }
    suspend fun setSportsDbKey(v: String) = context.dataStore.edit { it[SPORTSDB_KEY] = v.trim() }
    val dockCorner: Flow<String> = context.dataStore.data.map { it[DOCK_CORNER] ?: "BOTTOM_END" }
    suspend fun setDockCorner(v: String) = context.dataStore.edit { it[DOCK_CORNER] = v }
    val dockSizeStep: Flow<Int> = context.dataStore.data.map { (it[DOCK_SIZE_STEP] ?: 1).coerceIn(0, 2) }
    suspend fun setDockSizeStep(v: Int) = context.dataStore.edit { it[DOCK_SIZE_STEP] = v.coerceIn(0, 2) }
    val wakeWordEnabled: Flow<Boolean> = context.dataStore.data.map { it[WAKE_WORD_ENABLED] ?: false }
    suspend fun setWakeWordEnabled(v: Boolean) = context.dataStore.edit { it[WAKE_WORD_ENABLED] = v }
    val backupGateways: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[BACKUP_GATEWAYS].orEmpty().split('\n').mapNotNull {
            it.trim().takeIf { s -> s.isNotEmpty() }
        }
    }
    suspend fun setBackupGateways(list: List<String>) = context.dataStore.edit {
        it[BACKUP_GATEWAYS] = list.joinToString("\n")
    }
    val discordWebhook: Flow<String> = context.dataStore.data.map { it[DISCORD_WEBHOOK].orEmpty() }
    suspend fun setDiscordWebhook(v: String) = context.dataStore.edit { it[DISCORD_WEBHOOK] = v.trim() }
    val discordVoiceChannel: Flow<String> = context.dataStore.data.map {
        it[DISCORD_VOICE_CHANNEL].orEmpty().ifEmpty { "Richard's Hangout" }
    }
    suspend fun setDiscordVoiceChannel(v: String) = context.dataStore.edit { it[DISCORD_VOICE_CHANNEL] = v.trim() }
    val companionMode: Flow<Boolean> = context.dataStore.data.map { it[COMPANION_MODE] ?: false }
    suspend fun setCompanionMode(v: Boolean) = context.dataStore.edit { it[COMPANION_MODE] = v }
    suspend fun companionModeNow(): Boolean = companionMode.first()
    val uiSoundsEnabled: Flow<Boolean> = context.dataStore.data.map { it[UI_SOUNDS_ENABLED] ?: true }
    suspend fun setUiSoundsEnabled(v: Boolean) = context.dataStore.edit { it[UI_SOUNDS_ENABLED] = v }
    val kidsModeEnabled: Flow<Boolean> = context.dataStore.data.map { it[KIDS_MODE_ENABLED] ?: false }
    suspend fun setKidsModeEnabled(v: Boolean) = context.dataStore.edit { it[KIDS_MODE_ENABLED] = v }
    val hudAutoHideSec: Flow<Int> = context.dataStore.data.map { it[HUD_AUTOHIDE_SEC] ?: 8 }
    suspend fun setHudAutoHideSec(v: Int) = context.dataStore.edit { it[HUD_AUTOHIDE_SEC] = v.coerceIn(0, 60) }
    /**
     * Overrides the outgoing User-Agent. Blank = the app default.
     *
     * Providers block unfamiliar agents with 403 far more often than they
     * block anything else, so being able to present as a different client is
     * the single highest-yield workaround available.
     */
    /**
     * Whether the welcome video has already played. It is a first-run flourish,
     * not a loading screen — showing it on every launch would turn a nice
     * moment into a ten-second toll.
     */
    val welcomeSeen: Flow<Boolean> = context.dataStore.data.map { it[WELCOME_SEEN] ?: false }
    suspend fun setWelcomeSeen(v: Boolean) = context.dataStore.edit { it[WELCOME_SEEN] = v }

    val customUserAgent: Flow<String> = context.dataStore.data.map { it[CUSTOM_UA] ?: "" }
    suspend fun setCustomUserAgent(v: String) = context.dataStore.edit { it[CUSTOM_UA] = v.trim() }

    val vodForceMp4: Flow<Boolean> = context.dataStore.data.map { it[VOD_FORCE_MP4] ?: false }
    suspend fun setVodForceMp4(v: Boolean) = context.dataStore.edit { it[VOD_FORCE_MP4] = v }
    val tmdbApiKey: Flow<String> = context.dataStore.data.map { it[TMDB_API_KEY].orEmpty() }
    suspend fun setTmdbApiKey(v: String) = context.dataStore.edit { it[TMDB_API_KEY] = v.trim() }
    val decoderMode: Flow<String> = context.dataStore.data.map { it[DECODER_MODE] ?: "hwplus" }
    suspend fun setDecoderMode(v: String) = context.dataStore.edit { it[DECODER_MODE] = v }
    val minBufferMs: Flow<Int> = context.dataStore.data.map { it[MIN_BUFFER_MS] ?: 0 }
    suspend fun setMinBufferMs(v: Int) = context.dataStore.edit { it[MIN_BUFFER_MS] = v.coerceIn(0, 20_000) }
    val dialogueBoost: Flow<String> = context.dataStore.data.map { it[DIALOGUE_BOOST] ?: "off" }
    suspend fun setDialogueBoost(v: String) = context.dataStore.edit { it[DIALOGUE_BOOST] = v }
    val backgroundAudio: Flow<Boolean> = context.dataStore.data.map { it[BACKGROUND_AUDIO] ?: false }
    suspend fun setBackgroundAudio(v: Boolean) = context.dataStore.edit { it[BACKGROUND_AUDIO] = v }

    // v1.50.0 — per-type buffer profiles.
    val vodBufferProfile: Flow<String> = context.dataStore.data.map { it[VOD_BUFFER_PROFILE] ?: "auto" }
    suspend fun setVodBufferProfile(v: String) = context.dataStore.edit { it[VOD_BUFFER_PROFILE] = v }
    val liveBufferProfile: Flow<String> = context.dataStore.data.map { it[LIVE_BUFFER_PROFILE] ?: "auto" }
    suspend fun setLiveBufferProfile(v: String) = context.dataStore.edit { it[LIVE_BUFFER_PROFILE] = v }
    val vodMinBufferMs: Flow<Int> = context.dataStore.data.map { it[VOD_MIN_BUFFER_MS] ?: 25_000 }
    suspend fun setVodMinBufferMs(v: Int) = context.dataStore.edit { it[VOD_MIN_BUFFER_MS] = v.coerceIn(2_000, 60_000) }
    val vodMaxBufferMs: Flow<Int> = context.dataStore.data.map { it[VOD_MAX_BUFFER_MS] ?: 120_000 }
    suspend fun setVodMaxBufferMs(v: Int) = context.dataStore.edit { it[VOD_MAX_BUFFER_MS] = v.coerceIn(10_000, 300_000) }
    val vodPlaybackMs: Flow<Int> = context.dataStore.data.map { it[VOD_PLAYBACK_MS] ?: 2_000 }
    suspend fun setVodPlaybackMs(v: Int) = context.dataStore.edit { it[VOD_PLAYBACK_MS] = v.coerceIn(500, 10_000) }
    val vodRebufferMs: Flow<Int> = context.dataStore.data.map { it[VOD_REBUFFER_MS] ?: 5_000 }
    suspend fun setVodRebufferMs(v: Int) = context.dataStore.edit { it[VOD_REBUFFER_MS] = v.coerceIn(1_000, 15_000) }
    val liveMinBufferMs: Flow<Int> = context.dataStore.data.map { it[LIVE_MIN_BUFFER_MS] ?: 2_000 }
    suspend fun setLiveMinBufferMs(v: Int) = context.dataStore.edit { it[LIVE_MIN_BUFFER_MS] = v.coerceIn(500, 15_000) }
    val liveMaxBufferMs: Flow<Int> = context.dataStore.data.map { it[LIVE_MAX_BUFFER_MS] ?: 8_000 }
    suspend fun setLiveMaxBufferMs(v: Int) = context.dataStore.edit { it[LIVE_MAX_BUFFER_MS] = v.coerceIn(3_000, 30_000) }
    val livePlaybackMs: Flow<Int> = context.dataStore.data.map { it[LIVE_PLAYBACK_MS] ?: 500 }
    suspend fun setLivePlaybackMs(v: Int) = context.dataStore.edit { it[LIVE_PLAYBACK_MS] = v.coerceIn(200, 5_000) }
    val liveRebufferMs: Flow<Int> = context.dataStore.data.map { it[LIVE_REBUFFER_MS] ?: 1_500 }
    suspend fun setLiveRebufferMs(v: Int) = context.dataStore.edit { it[LIVE_REBUFFER_MS] = v.coerceIn(500, 8_000) }
    val allocatorSizeKb: Flow<Int> = context.dataStore.data.map { it[ALLOCATOR_SIZE_KB] ?: 0 }
    suspend fun setAllocatorSizeKb(v: Int) = context.dataStore.edit { it[ALLOCATOR_SIZE_KB] = v.coerceIn(0, 4096) }
    val trialUsed: Flow<Boolean> = context.dataStore.data.map { it[TRIAL_USED] ?: false }
    suspend fun setTrialUsed(v: Boolean) = context.dataStore.edit { it[TRIAL_USED] = v }
    suspend fun trialUsedNow(): Boolean = trialUsed.first()
    val trialExpiresAt: Flow<Long> = context.dataStore.data.map { it[TRIAL_EXPIRES_AT] ?: 0L }
    suspend fun setTrialExpiresAt(v: Long) = context.dataStore.edit { it[TRIAL_EXPIRES_AT] = v }
    suspend fun trialExpiresAtNow(): Long = trialExpiresAt.first()
    val downloadEngine: Flow<String> = context.dataStore.data.map { it[DOWNLOAD_ENGINE] ?: "auto" }
    suspend fun setDownloadEngine(v: String) = context.dataStore.edit { it[DOWNLOAD_ENGINE] = v }
    suspend fun downloadEngineNow(): String = downloadEngine.first()
    val downloadFolderUri: Flow<String> = context.dataStore.data.map { it[DOWNLOAD_FOLDER_URI].orEmpty() }
    suspend fun setDownloadFolderUri(v: String) = context.dataStore.edit { it[DOWNLOAD_FOLDER_URI] = v.trim() }
    suspend fun downloadFolderUriNow(): String = downloadFolderUri.first()
    val autoTrailersEnabled: Flow<Boolean> = context.dataStore.data.map { it[AUTO_TRAILERS] ?: true }
    suspend fun setAutoTrailersEnabled(v: Boolean) = context.dataStore.edit { it[AUTO_TRAILERS] = v }
    val matchCenterEnabled: Flow<Boolean> = context.dataStore.data.map { it[MATCH_CENTER] ?: true }
    suspend fun setMatchCenterEnabled(v: Boolean) = context.dataStore.edit { it[MATCH_CENTER] = v }

    // Defaults: 60 % video in landscape (unchanged from the old hardcoded
    // weight), and 42 % in portrait — a stacked 16:9 video only needs about
    // that much height, and the old aspect-locked pane left the dock cramped.
    // 55 % for the channel list, up from the old 62 %, because the guide
    // column was truncating almost every programme title at 38 %.
    val downloadsWifiOnly: Flow<Boolean> = context.dataStore.data.map {
        it[DOWNLOADS_WIFI_ONLY] ?: (tv.enktel.app.BuildConfig.FLAVOR == "mobile")
    }
    suspend fun setDownloadsWifiOnly(v: Boolean) = context.dataStore.edit { it[DOWNLOADS_WIFI_ONLY] = v }
    suspend fun downloadsWifiOnlyNow(): Boolean = downloadsWifiOnly.first()

    val browseSplitLandscape: Flow<Float> =
        context.dataStore.data.map { (it[BROWSE_SPLIT_LAND] ?: 60) / 100f }
    suspend fun setBrowseSplitLandscape(v: Float) = context.dataStore.edit {
        it[BROWSE_SPLIT_LAND] = (v * 100).toInt().coerceIn(20, 85)
    }
    val browseSplitPortrait: Flow<Float> =
        context.dataStore.data.map { (it[BROWSE_SPLIT_PORT] ?: 42) / 100f }
    suspend fun setBrowseSplitPortrait(v: Float) = context.dataStore.edit {
        it[BROWSE_SPLIT_PORT] = (v * 100).toInt().coerceIn(20, 85)
    }
    val dockSplit: Flow<Float> =
        context.dataStore.data.map { (it[DOCK_SPLIT] ?: 55) / 100f }
    suspend fun setDockSplit(v: Float) = context.dataStore.edit {
        it[DOCK_SPLIT] = (v * 100).toInt().coerceIn(20, 85)
    }

    fun categoryOrder(kind: String): Flow<List<String>> = context.dataStore.data.map { prefs ->
        val key = when (kind) { "vod" -> VOD_CAT_ORDER; "series" -> SERIES_CAT_ORDER; else -> LIVE_CAT_ORDER }
        prefs[key]?.split('|')?.filter(String::isNotBlank).orEmpty()
    }
    fun hiddenCategories(kind: String): Flow<Set<String>> = context.dataStore.data.map { prefs ->
        val key = when (kind) { "vod" -> VOD_HIDDEN_CATS; "series" -> SERIES_HIDDEN_CATS; else -> LIVE_HIDDEN_CATS }
        prefs[key].orEmpty()
    }
    suspend fun setCategoryOrder(kind: String, order: List<String>) = context.dataStore.edit { prefs ->
        val key = when (kind) { "vod" -> VOD_CAT_ORDER; "series" -> SERIES_CAT_ORDER; else -> LIVE_CAT_ORDER }
        prefs[key] = order.joinToString("|")
    }
    suspend fun setHiddenCategories(kind: String, set: Set<String>) = context.dataStore.edit { prefs ->
        val key = when (kind) { "vod" -> VOD_HIDDEN_CATS; "series" -> SERIES_HIDDEN_CATS; else -> LIVE_HIDDEN_CATS }
        prefs[key] = set
    }

    suspend fun activeProfileIdNow(): Long = activeProfileId.first()
    suspend fun setActiveProfile(id: Long) = context.dataStore.edit { it[ACTIVE_PROFILE] = id }
    suspend fun setStreamFormat(v: String) = context.dataStore.edit { it[STREAM_FORMAT] = v }
    suspend fun setBufferProfile(v: String) = context.dataStore.edit { it[BUFFER_PROFILE] = v }
    suspend fun setLastChannel(key: String) = context.dataStore.edit { it[LAST_CHANNEL] = key }
    suspend fun setAutoplayLast(v: Boolean) = context.dataStore.edit { it[AUTOPLAY_LAST] = v }
    suspend fun setEpgLastSync(t: Long) = context.dataStore.edit { it[EPG_LAST_SYNC] = t }
    suspend fun epgLastSync(): Long = context.dataStore.data.map { it[EPG_LAST_SYNC] ?: 0L }.first()

    suspend fun setParentalPin(hash: String) = context.dataStore.edit { it[PARENTAL_PIN] = hash }
    suspend fun setLockedCategories(set: Set<String>) = context.dataStore.edit { it[LOCKED_CATEGORIES] = set }
    suspend fun setEpgOffsetMin(v: Int) = context.dataStore.edit { it[EPG_OFFSET_MIN] = v }

    /** Read once, for EpgRepository — see EpgShift. */
    suspend fun epgOffsetMinNow(): Int = epgOffsetMin.first()
    suspend fun setSubScalePct(v: Int) = context.dataStore.edit { it[SUB_SCALE] = v }
    suspend fun setSubStyle(v: String) = context.dataStore.edit { it[SUB_STYLE] = v }
    suspend fun setRecPrefixMin(v: Int) = context.dataStore.edit { it[REC_PREFIX_MIN] = v }
    suspend fun setRecSuffixMin(v: Int) = context.dataStore.edit { it[REC_SUFFIX_MIN] = v }
    suspend fun setAutoEpgHours(v: Int) = context.dataStore.edit { it[AUTO_EPG_HOURS] = v }
    suspend fun setHiddenChannels(set: Set<String>) = context.dataStore.edit { it[HIDDEN_CHANNELS] = set }

    /**
     * Hide or unhide one channel.
     *
     * Read-modify-write inside a single `edit` block rather than via the
     * exposed flow: two rapid taps against a flow read would both see the
     * pre-edit set and the second would undo the first.
     */
    suspend fun toggleHiddenChannel(key: String) = context.dataStore.edit { prefs ->
        val cur = prefs[HIDDEN_CHANNELS] ?: emptySet()
        prefs[HIDDEN_CHANNELS] = if (key in cur) cur - key else cur + key
    }
    suspend fun setVodSort(v: String) = context.dataStore.edit { it[VOD_SORT] = v }
    suspend fun pushRecentChannel(key: String) = context.dataStore.edit { prefs ->
        val current = prefs[RECENT_CHANNELS]?.split('|')?.filter(String::isNotBlank).orEmpty()
        val merged = (listOf(key) + current.filter { it != key }).take(15)
        prefs[RECENT_CHANNELS] = merged.joinToString("|")
    }
    suspend fun setLiveShiftEnabled(v: Boolean) = context.dataStore.edit { it[LIVE_SHIFT_ENABLED] = v }

    suspend fun setLoudnessOn(v: Boolean) = context.dataStore.edit { it[LOUDNESS_ON] = v }
    suspend fun setSubColor(v: String) = context.dataStore.edit { it[SUB_COLOR] = v }
    suspend fun setSubEdge(v: String) = context.dataStore.edit { it[SUB_EDGE] = v }
    suspend fun setSubBgAlpha(v: Int) = context.dataStore.edit { it[SUB_BG_ALPHA] = v }
    suspend fun setExtSubUrl(v: String) = context.dataStore.edit { it[EXT_SUB_URL] = v }
    suspend fun setAutoplayNextEp(v: Boolean) = context.dataStore.edit { it[AUTOPLAY_NEXT_EP] = v }
    suspend fun setTunneling(v: Boolean) = context.dataStore.edit { it[TUNNELING] = v }
    suspend fun setRelayPlayback(v: Boolean) = context.dataStore.edit { it[RELAY_PLAYBACK] = v }
    suspend fun setRelayBase(v: String) = context.dataStore.edit { it[RELAY_BASE] = v.trim() }
    suspend fun setProxyEndpoint(v: String) = context.dataStore.edit { it[PROXY_ENDPOINT] = v.trim() }
    suspend fun setProxyUser(v: String) = context.dataStore.edit { it[PROXY_USER] = v.trim() }
    suspend fun setProxyPass(v: String) = context.dataStore.edit { it[PROXY_PASS] = v }
    suspend fun setSkipIntroSec(v: Int) = context.dataStore.edit { it[SKIP_INTRO_SEC] = v }
    suspend fun setPipEnabled(v: Boolean) = context.dataStore.edit { it[PIP_ENABLED] = v }
    suspend fun setAutoPipOnBack(v: Boolean) = context.dataStore.edit { it[AUTO_PIP_ON_BACK] = v }
    suspend fun setAutoPipOnHome(v: Boolean) = context.dataStore.edit { it[AUTO_PIP_ON_HOME] = v }
    suspend fun setScreensaverMin(v: Int) = context.dataStore.edit { it[SCREENSAVER_MIN] = v }
    suspend fun setFirstRunDone(v: Boolean) = context.dataStore.edit { it[FIRST_RUN_DONE] = v }
    suspend fun setScoresEnabled(v: Boolean) = context.dataStore.edit { it[SCORES_ENABLED] = v }
    suspend fun setHiddenItems(set: Set<String>) = context.dataStore.edit { it[HIDDEN_ITEMS] = set }

    suspend fun setTheme(v: String) = context.dataStore.edit { it[THEME] = v }
    suspend fun setUiOpacityPct(v: Int) = context.dataStore.edit { it[UI_OPACITY_PCT] = v.coerceIn(60, 100) }
    suspend fun setTextScalePct(v: Int) = context.dataStore.edit { it[TEXT_SCALE_PCT] = v.coerceIn(85, 140) }
    suspend fun setStartOnBoot(v: Boolean) = context.dataStore.edit { it[START_ON_BOOT] = v }
    suspend fun startOnBootNow(): Boolean = startOnBoot.first()
    suspend fun setBackAction(v: String) = context.dataStore.edit { it[BACK_ACTION] = v }
}
