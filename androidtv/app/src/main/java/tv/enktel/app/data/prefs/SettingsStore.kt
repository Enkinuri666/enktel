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
    val vodSort: Flow<String> = context.dataStore.data.map { it[VOD_SORT] ?: "name" } // name|rating|added
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
    val skipIntroSec: Flow<Int> = context.dataStore.data.map { it[SKIP_INTRO_SEC] ?: 0 }
    val pipEnabled: Flow<Boolean> = context.dataStore.data.map { it[PIP_ENABLED] ?: true }
    val autoPipOnBack: Flow<Boolean> = context.dataStore.data.map { it[AUTO_PIP_ON_BACK] ?: true }
    val autoPipOnHome: Flow<Boolean> = context.dataStore.data.map { it[AUTO_PIP_ON_HOME] ?: true }
    val screensaverMin: Flow<Int> = context.dataStore.data.map { it[SCREENSAVER_MIN] ?: 5 }
    val firstRunDone: Flow<Boolean> = context.dataStore.data.map { it[FIRST_RUN_DONE] ?: false }
    val scoresEnabled: Flow<Boolean> = context.dataStore.data.map { it[SCORES_ENABLED] ?: false }
    val hiddenItems: Flow<Set<String>> = context.dataStore.data.map { it[HIDDEN_ITEMS] ?: emptySet() }

    val theme: Flow<String> = context.dataStore.data.map { it[THEME] ?: "enktel_blue" }
    val uiOpacityPct: Flow<Int> = context.dataStore.data.map { it[UI_OPACITY_PCT] ?: 92 }
    val textScalePct: Flow<Int> = context.dataStore.data.map { it[TEXT_SCALE_PCT] ?: 100 }
    val startOnBoot: Flow<Boolean> = context.dataStore.data.map { it[START_ON_BOOT] ?: false }

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
    suspend fun setSubScalePct(v: Int) = context.dataStore.edit { it[SUB_SCALE] = v }
    suspend fun setSubStyle(v: String) = context.dataStore.edit { it[SUB_STYLE] = v }
    suspend fun setRecPrefixMin(v: Int) = context.dataStore.edit { it[REC_PREFIX_MIN] = v }
    suspend fun setRecSuffixMin(v: Int) = context.dataStore.edit { it[REC_SUFFIX_MIN] = v }
    suspend fun setAutoEpgHours(v: Int) = context.dataStore.edit { it[AUTO_EPG_HOURS] = v }
    suspend fun setHiddenChannels(set: Set<String>) = context.dataStore.edit { it[HIDDEN_CHANNELS] = set }
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
}
