package tv.enktel.app.data.repo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import tv.enktel.app.data.db.AppDatabase
import tv.enktel.app.data.db.EpgProgram
import tv.enktel.app.data.db.Profile
import tv.enktel.app.data.xtream.XtreamClient
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import tv.enktel.app.data.epg.XmltvParser

data class NowNext(val now: EpgProgram?, val next: EpgProgram?)

class EpgRepository(
    private val db: AppDatabase,
    private val xtream: XtreamClient,
    private val http: OkHttpClient,
) {
    private val epg get() = db.epgDao()

    suspend fun refresh(p: Profile): Int = withContext(Dispatchers.IO) {
        val url = when {
            p.kind == "xtream" -> XtreamClient.xmltvUrl(p)
            p.epgUrl.isNotBlank() -> p.epgUrl
            else -> return@withContext 0
        }
        val wanted = db.contentDao().channels(p.id).first()
            .mapNotNull { it.epgId.takeIf { id -> id.isNotBlank() } }
            .toHashSet()

        val now = System.currentTimeMillis()
        val from = now - TimeUnit.DAYS.toMillis(8)
        val to = now + TimeUnit.DAYS.toMillis(8)

        val req = Request.Builder().url(url).build()
        val total = http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("EPG returned HTTP ${resp.code}")
            val raw = resp.body.byteStream()
            val stream = maybeGunzip(raw, url, resp.header("Content-Type"), resp.header("Content-Encoding"))
            epg.clear(p.id)
            XmltvParser.parse(stream, p.id, wanted, from, to) { batch -> epg.insertAll(batch) }
        }
        epg.prune(now - TimeUnit.DAYS.toMillis(9))
        total
    }

    private fun maybeGunzip(input: InputStream, url: String, contentType: String?, encoding: String?): InputStream {
        val buffered = input.buffered(64 * 1024)
        if (encoding?.contains("gzip", true) == true) return buffered // OkHttp already decoded explicit gzip bodies
        buffered.mark(2)
        val b1 = buffered.read(); val b2 = buffered.read()
        buffered.reset()
        val isGz = b1 == 0x1f && b2 == 0x8b
        return if (isGz || url.endsWith(".gz") && isGz) GZIPInputStream(buffered) else buffered
    }

    suspend fun nowNext(profileId: Long, epgId: String): NowNext {
        if (epgId.isBlank()) return NowNext(null, null)
        val now = System.currentTimeMillis()
        val list = epg.nowNext(profileId, epgId, now, 2)
        val current = list.firstOrNull { it.startMs <= now && it.endMs > now }
        val next = list.firstOrNull { it.startMs > now }
        return NowNext(current, next)
    }

    suspend fun upcoming(profileId: Long, epgId: String, n: Int = 8): List<EpgProgram> {
        if (epgId.isBlank()) return emptyList()
        return epg.nowNext(profileId, epgId, System.currentTimeMillis(), n)
    }

    suspend fun window(profileId: Long, epgIds: List<String>, from: Long, to: Long): Map<String, List<EpgProgram>> {
        if (epgIds.isEmpty()) return emptyMap()
        return epgIds.chunked(400).flatMap { chunk -> epg.windowMany(profileId, chunk, from, to) }
            .groupBy { it.epgId }
    }

    suspend fun archive(profileId: Long, epgId: String, daysBack: Int): List<EpgProgram> {
        if (epgId.isBlank()) return emptyList()
        val now = System.currentTimeMillis()
        return epg.archive(profileId, epgId, now - TimeUnit.DAYS.toMillis(daysBack.toLong().coerceAtLeast(1)), now)
    }

    suspend fun hasData(profileId: Long) = epg.count(profileId) > 0
}
