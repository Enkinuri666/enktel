package tv.enktel.app.data.m3u

import java.io.BufferedReader

data class M3uEntry(
    val name: String,
    val url: String,
    val tvgId: String,
    val tvgName: String,
    val logo: String,
    val group: String,
    val chno: Int,
    val catchupDays: Int,
    /** `radio="true"` on the #EXTINF line. */
    val isRadio: Boolean = false,
    /** `#EXTVLCOPT:http-user-agent=` for this entry only. */
    val userAgent: String = "",
) {
    val isVod: Boolean
        get() {
            val lower = url.substringBefore('?').lowercase()
            if (VOD_EXT.any { lower.endsWith(it) }) return true
            val g = group.lowercase()
            return "vod" in g || "movie" in g || "film" in g
        }

    companion object {
        private val VOD_EXT = listOf(".mp4", ".mkv", ".avi", ".mov", ".flv", ".wmv", ".webm")
    }
}

data class M3uPlaylist(val entries: List<M3uEntry>, val epgUrl: String)

object M3uParser {
    private val ATTR = Regex("""([\w-]+)="(.*?)"""")

    fun parse(reader: BufferedReader): M3uPlaylist {
        val entries = ArrayList<M3uEntry>(1024)
        var epgUrl = ""
        var attrs: Map<String, String> = emptyMap()
        var title = ""
        var pending = false
        var autoNum = 0
        // #EXTVLCOPT lines sit between the #EXTINF and the URL, so the value has
        // to be carried across iterations and cleared once it has been consumed
        // by the entry it belongs to -- otherwise one channel's override leaks
        // onto every channel below it.
        var vlcUa = ""

        reader.forEachLine { raw ->
            val line = raw.trim()
            when {
                line.startsWith("#EXTM3U") -> {
                    epgUrl = ATTR.findAll(line)
                        .firstOrNull { it.groupValues[1] == "url-tvg" || it.groupValues[1] == "x-tvg-url" }
                        ?.groupValues?.get(2) ?: ""
                }
                line.startsWith("#EXTINF") -> {
                    attrs = ATTR.findAll(line).associate { it.groupValues[1] to it.groupValues[2] }
                    title = line.substringAfterLast(',').trim()
                    pending = true
                }
                // Per-channel HTTP options. `http-user-agent` is the one that
                // matters in practice: plenty of sources answer for exactly one
                // User-Agent and 403 everything else, which a single global
                // override cannot fix without breaking the rest of the playlist.
                pending && line.startsWith("#EXTVLCOPT", true) -> {
                    val v = line.substringAfter(':', "").trim()
                    if (v.startsWith("http-user-agent=", true)) {
                        vlcUa = v.substringAfter('=').trim()
                    }
                }
                // Group on its own line, an older spelling of group-title.
                pending && line.startsWith("#EXTGRP", true) -> {
                    val g = line.substringAfter(':', "").trim()
                    if (g.isNotEmpty()) attrs = attrs + ("group-title" to g)
                }
                pending && line.isNotEmpty() && !line.startsWith("#") -> {
                    autoNum++
                    entries += M3uEntry(
                        name = attrs["tvg-name"].orEmpty().ifBlank { title }.ifBlank { "Channel $autoNum" },
                        url = line,
                        tvgId = attrs["tvg-id"].orEmpty(),
                        tvgName = attrs["tvg-name"].orEmpty(),
                        logo = attrs["tvg-logo"].orEmpty(),
                        group = attrs["group-title"].orEmpty().ifBlank { "Uncategorized" },
                        chno = attrs["tvg-chno"]?.toIntOrNull() ?: autoNum,
                        catchupDays = attrs["catchup-days"]?.toIntOrNull()
                            ?: if (attrs.containsKey("catchup")) 1 else 0,
                        isRadio = attrs["radio"].equals("true", true),
                        userAgent = vlcUa,
                    )
                    pending = false
                    vlcUa = ""
                }
            }
        }
        return M3uPlaylist(entries, epgUrl)
    }
}
