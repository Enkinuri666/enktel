package tv.enktel.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("enktel_settings")

class SettingsStore(private val context: Context) {
    private val ACTIVE_PROFILE = longPreferencesKey("active_profile")
    private val STREAM_FORMAT = stringPreferencesKey("stream_format") // hls | ts
    private val BUFFER_PROFILE = stringPreferencesKey("buffer_profile") // low | balanced | large
    private val LAST_CHANNEL = stringPreferencesKey("last_channel")
    private val EPG_LAST_SYNC = longPreferencesKey("epg_last_sync")
    private val AUTOPLAY_LAST = booleanPreferencesKey("autoplay_last")
    private val GUIDE_HOURS = intPreferencesKey("guide_hours")

    val activeProfileId: Flow<Long> = context.dataStore.data.map { it[ACTIVE_PROFILE] ?: 0L }
    val streamFormat: Flow<String> = context.dataStore.data.map { it[STREAM_FORMAT] ?: "hls" }
    val bufferProfile: Flow<String> = context.dataStore.data.map { it[BUFFER_PROFILE] ?: "balanced" }
    val lastChannel: Flow<String> = context.dataStore.data.map { it[LAST_CHANNEL] ?: "" }
    val autoplayLast: Flow<Boolean> = context.dataStore.data.map { it[AUTOPLAY_LAST] ?: true }

    suspend fun activeProfileIdNow(): Long = activeProfileId.first()
    suspend fun setActiveProfile(id: Long) = context.dataStore.edit { it[ACTIVE_PROFILE] = id }
    suspend fun setStreamFormat(v: String) = context.dataStore.edit { it[STREAM_FORMAT] = v }
    suspend fun setBufferProfile(v: String) = context.dataStore.edit { it[BUFFER_PROFILE] = v }
    suspend fun setLastChannel(key: String) = context.dataStore.edit { it[LAST_CHANNEL] = key }
    suspend fun setAutoplayLast(v: Boolean) = context.dataStore.edit { it[AUTOPLAY_LAST] = v }
    suspend fun setEpgLastSync(t: Long) = context.dataStore.edit { it[EPG_LAST_SYNC] = t }
    suspend fun epgLastSync(): Long = context.dataStore.data.map { it[EPG_LAST_SYNC] ?: 0L }.first()
}
