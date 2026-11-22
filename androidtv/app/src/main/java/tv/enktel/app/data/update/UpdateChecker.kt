package tv.enktel.app.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * "Is there a newer APK on GitHub than what this build is?"
 *
 * Sideloaded users (Fire TV, Android TV, phones outside the Play Store) have
 * no update prompt by default, so the app has to ask on their behalf — once
 * per visit to Settings, against the published release feed. What this
 * returns is an invitation to open the release page, not an installer: a
 * PackageInstaller handoff needs REQUEST_INSTALL_PACKAGES, which is a Settings
 * trip of its own for every user, every reinstall. Opening the release page in
 * a browser is the same number of clicks they are already used to and asks
 * no extra permission.
 *
 * Reads the `latest` endpoint rather than `/releases` so the result is one
 * JSON object, which stays under a kilobyte — the whole check costs one
 * roundtrip whatever the release history looks like.
 */
class UpdateChecker(private val http: OkHttpClient) {

    /**
     * What the "latest" endpoint returns, pared down to what we use.
     *
     * `@SerialName` not set because the server's keys and our field names
     * agree. `ignoreUnknownKeys` in the parser handles the rest of the
     * payload (there are a lot of per-asset fields) so adding one does not
     * break the check.
     */
    @Serializable
    private data class LatestRelease(
        val tag_name: String = "",
        val name: String = "",
        val html_url: String = "",
        val body: String = "",
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val assets: List<ReleaseAsset> = emptyList(),
    )

    @Serializable
    private data class ReleaseAsset(
        val name: String = "",
        val browser_download_url: String = "",
        val digest: String = "",
        val size: Long = 0,
    )

    /**
     * What a caller gets when there IS an update.
     *
     * [assetUrl] matches the current flavour when the release carries one for
     * it (every release since 1.69 has shipped tv + mobile APKs). [digest] is
     * the SHA-256 the release publishes; a hash check on the downloaded APK
     * is the one safety belt that still catches a malicious mirror if
     * somebody ever substitutes the browser URL.
     */
    data class UpdateInfo(
        val currentVersion: String,
        val latestVersion: String,
        val releaseUrl: String,
        val releaseNotes: String,
        val assetUrl: String?,
        val assetSize: Long,
        val digest: String?,
    )

    /**
     * Returns the update that is newer than [currentVersion], or null when
     * the build is already on the latest.
     *
     * [flavor] is "tv" or "mobile"; the asset whose filename starts with
     * `enktel-$flavor-` is handed back. A flavour the current release did not
     * ship for returns the release page but no download URL.
     *
     * Any network failure is swallowed and null is returned: a settings
     * screen is not the right place to shout about a flaky connection.
     */
    suspend fun check(currentVersion: String, flavor: String): UpdateInfo? =
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url(RELEASES_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "EnkTel-Android")
                .build()
            val body = runCatching {
                http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    resp.body?.string()
                }
            }.getOrNull() ?: return@withContext null
            val rel = runCatching { parser.decodeFromString<LatestRelease>(body) }.getOrNull()
                ?: return@withContext null
            if (rel.draft || rel.prerelease) return@withContext null

            val latest = rel.tag_name.removePrefix("v")
            if (!isNewer(current = currentVersion, latest = latest)) return@withContext null

            val wanted = rel.assets.firstOrNull { it.name.startsWith("enktel-$flavor-") }
            UpdateInfo(
                currentVersion = currentVersion,
                latestVersion = latest,
                releaseUrl = rel.html_url,
                releaseNotes = rel.body,
                assetUrl = wanted?.browser_download_url?.takeIf { it.isNotBlank() },
                assetSize = wanted?.size ?: 0,
                digest = wanted?.digest?.takeIf { it.isNotBlank() },
            )
        }

    companion object {
        private const val RELEASES_URL =
            "https://api.github.com/repos/Enkinuri666/enktel/releases/latest"

        private val parser = Json { ignoreUnknownKeys = true }

        /**
         * `1.85.0` vs `1.84.3`, treated as numeric triples so `1.10.0` is not
         * read as smaller than `1.9.0`. Suffixes on either side (the mobile
         * flavour carries `-mobile`) are dropped; a tag that will not parse
         * into three numbers is treated as "not newer" so a malformed tag can
         * never nag the user into reinstalling.
         */
        internal fun isNewer(current: String, latest: String): Boolean {
            val c = triple(current) ?: return false
            val l = triple(latest) ?: return false
            return when {
                l[0] != c[0] -> l[0] > c[0]
                l[1] != c[1] -> l[1] > c[1]
                else -> l[2] > c[2]
            }
        }

        private fun triple(v: String): IntArray? {
            val core = v.substringBefore('-').trim()
            val parts = core.split('.')
            if (parts.size != 3) return null
            val out = IntArray(3)
            for (i in 0..2) {
                out[i] = parts[i].toIntOrNull() ?: return null
            }
            return out
        }
    }
}
