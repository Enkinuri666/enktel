package tv.enktel.app.data.repo

import java.net.URLDecoder

/**
 * Works out what a subscriber just pasted.
 *
 * ## Why this exists
 *
 * The setup form asked, in order: Xtream Codes or M3U Playlist; a playlist
 * name; a server URL "(http://host:port)"; a username; a password; and an
 * optional film-library M3U. Six decisions, the first of which is a protocol
 * question, before anyone can watch anything. The feedback was that the app is
 * hard to set up, and this form is most of why.
 *
 * None of those questions are ones the subscriber has an answer to. What they
 * *have* is whatever their provider sent them — for our own subscribers, the
 * welcome email, which carries a server address, a username, a password and an
 * M3U link. Every one of those already says which kind of line it is and, in
 * the M3U case, carries the credentials in its query string.
 *
 * So the form can ask for one thing: paste it. This turns the paste into a
 * [Setup].
 *
 * ## What it accepts
 *
 * - `http://host:8080/get.php?username=u&password=p&type=m3u_plus` — the M3U
 *   link from a welcome email. Yields an **Xtream** line, not an M3U one: the
 *   credentials are right there, and an Xtream profile gets categories, VOD,
 *   series and catch-up that a flat playlist cannot.
 * - `http://host:8080/player_api.php?username=u&password=p` — same.
 * - `http://host:8080` plus a username and password typed separately.
 * - Any other playlist URL (`.m3u`, `.m3u8`, an IPTV-Editor or Xtream-proxy
 *   link) — an M3U line.
 * - The welcome email's credentials block pasted whole, newlines and labels
 *   and all:
 *
 *       Username: enktel_demo
 *       Password: 8fj3kd92
 *       Server address: http://host:8080
 *
 * ## What it deliberately does not do
 *
 * It does not reach the network. Deciding what something *is* and finding out
 * whether it *works* are different jobs, and only the second one can fail
 * slowly or leave a half-made profile behind. [PlaylistRepository.addXtream]
 * still validates, still probes both schemes, and still reports the panel's
 * own refusal.
 */
object SetupLink {

    /** What a paste turned out to be. */
    sealed interface Setup {
        /** A panel line: server plus credentials. */
        data class Xtream(
            val server: String,
            val username: String,
            val password: String,
        ) : Setup

        /** A playlist URL, with no credentials to pull out of it. */
        data class M3u(val url: String) : Setup

        /**
         * A server was recognised but no credentials came with it.
         *
         * Not a failure: it is the normal state of the form after someone has
         * pasted a bare host, and the caller's job is to ask for the two
         * fields rather than to complain.
         */
        data class NeedsCredentials(val server: String) : Setup

        /** Nothing usable. [reason] is written to be shown to a subscriber. */
        data class Unrecognised(val reason: String) : Setup
    }

    /** Endpoints whose path carries credentials we can lift. */
    private val CREDENTIALLED = listOf("get.php", "player_api.php", "panel_api.php", "xmltv.php")

    /**
     * Labels the welcome email and most providers use, lowercased.
     *
     * Matched on a line's prefix rather than searched for anywhere in it: a
     * password can contain the word "user", and a line-leading label is the
     * only position that reliably means "this is the field name".
     */
    private val USER_LABELS = listOf("username", "user name", "user", "login")
    private val PASS_LABELS = listOf("password", "pass", "pwd")
    private val SERVER_LABELS = listOf("server address", "server url", "server", "host", "portal", "url", "dns")

    /**
     * Parse a paste, optionally with a username and password the subscriber
     * typed into their own fields.
     *
     * [typedUser] and [typedPass] win over anything found in the text: if
     * someone has filled the fields in, that is a more recent statement of
     * intent than whatever the pasted link happens to contain.
     */
    fun parse(raw: String, typedUser: String = "", typedPass: String = ""): Setup {
        val text = raw.trim()
        if (text.isBlank() && typedUser.isBlank()) {
            return Setup.Unrecognised("Paste the link or details your provider sent you.")
        }

        val fromLabels = readLabelled(text)
        val url = firstUrl(text)

        val user = typedUser.trim().ifBlank { fromLabels.user }
        val pass = typedPass.trim().ifBlank { fromLabels.pass }

        // A credentialled endpoint is the best case: it names the host and
        // carries the login, so nothing has to be typed at all.
        if (url != null && isCredentialled(url)) {
            val q = queryParams(url)
            val u = user.ifBlank { q["username"].orEmpty() }
            val p = pass.ifBlank { q["password"].orEmpty() }
            val server = PlaylistRepository.normalizeServer(url)
            return if (u.isNotBlank() && p.isNotBlank()) {
                Setup.Xtream(server, u, p)
            } else {
                // The shape said Xtream but the credentials are not in it —
                // an edited link, or one whose parameters are named something
                // else. Ask for the two fields rather than guessing.
                Setup.NeedsCredentials(server)
            }
        }

        // A plain playlist link. Only treated as M3U when there is no username
        // to pair it with: a subscriber who pasted a playlist *and* typed a
        // login is describing a panel, and a panel line is the better profile.
        if (url != null && looksLikePlaylist(url) && user.isBlank()) {
            return Setup.M3u(url)
        }

        val server = url?.let { PlaylistRepository.normalizeServer(it) }
            ?: fromLabels.server.takeIf { it.isNotBlank() }?.let { PlaylistRepository.normalizeServer(it) }

        if (server != null) {
            return if (user.isNotBlank() && pass.isNotBlank()) {
                Setup.Xtream(server, user, pass)
            } else {
                Setup.NeedsCredentials(server)
            }
        }

        // No address anywhere. Said in terms of what to do next rather than
        // what failed — "could not parse input" helps nobody holding a phone.
        return Setup.Unrecognised(
            "That doesn't look like a playlist link. Paste the whole line your " +
                "provider sent, starting with http.",
        )
    }

    /** A sensible profile name, so nobody has to invent one. */
    fun suggestedName(setup: Setup): String = when (setup) {
        is Setup.Xtream -> hostOf(setup.server)
        is Setup.M3u -> hostOf(setup.url)
        is Setup.NeedsCredentials -> hostOf(setup.server)
        is Setup.Unrecognised -> "My Playlist"
    }

    private fun hostOf(url: String): String =
        runCatching {
            java.net.URI(if (url.contains("://")) url else "http://$url").host
                ?.removePrefix("www.")
                ?.takeIf { it.isNotBlank() }
        }.getOrNull() ?: "My Playlist"

    // ── the pieces ────────────────────────────────────────────────────

    private data class Labelled(val user: String, val pass: String, val server: String)

    /**
     * Pull `Username: x` / `Password: y` / `Server: z` out of pasted text.
     *
     * Split on the first `:` or `=` after the label, because "Server address:
     * http://host:8080" contains three colons and only the first one is the
     * separator.
     */
    private fun readLabelled(text: String): Labelled {
        var user = ""
        var pass = ""
        var server = ""
        for (line in text.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val cut = trimmed.indexOfFirst { it == ':' || it == '=' }
            if (cut <= 0) continue
            val label = trimmed.take(cut).trim().lowercase().trimEnd('*', '-', '•', ' ')
            val value = trimmed.substring(cut + 1).trim()
            if (value.isBlank()) continue
            when {
                // Checked longest-first within each set so "user name" is not
                // eaten by "user".
                user.isBlank() && USER_LABELS.any { label == it } -> user = value
                pass.isBlank() && PASS_LABELS.any { label == it } -> pass = value
                server.isBlank() && SERVER_LABELS.any { label == it } -> server = value
            }
        }
        return Labelled(user, pass, server)
    }

    /** The first http(s) address in the text, or null. */
    private fun firstUrl(text: String): String? =
        Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
            .find(text)
            ?.value
            // Trailing punctuation from prose — "…&output=ts." or a link at
            // the end of a sentence inside a quoted email.
            ?.trimEnd('.', ',', ';', ')', '>', '"', '\'')

    /**
     * Is this one of the panel endpoints, whether or not it carries a login?
     *
     * Deliberately not `&& contains("username=")`. A `/get.php` link with its
     * credentials stripped is still a panel address — it is simply missing the
     * two fields — and treating it as a flat playlist instead would build an
     * M3U profile that can never load, off a URL that plainly names a panel.
     * The caller returns [Setup.NeedsCredentials] for that case.
     */
    private fun isCredentialled(url: String): Boolean {
        val lower = url.lowercase()
        return CREDENTIALLED.any { lower.contains("/$it") }
    }

    /**
     * Does this look like a playlist rather than a panel address?
     *
     * A path or a query is the tell: `http://host:8080` is a panel, whereas
     * anything with something after the host is a link to a specific file or
     * endpoint. `.m3u`/`.m3u8` are named explicitly because they are the
     * common case and worth being certain about.
     */
    private fun looksLikePlaylist(url: String): Boolean {
        val lower = url.lowercase()
        if (lower.contains(".m3u")) return true
        val afterScheme = lower.substringAfter("://")
        val path = afterScheme.substringAfter('/', "")
        return path.isNotBlank()
    }

    /**
     * Query parameters, decoded.
     *
     * Hand-rolled rather than `Uri.parse`: this is in `data/`, it is covered
     * by a plain JVM test, and pulling in the Android URI parser would make
     * that test need Robolectric for two string splits.
     */
    private fun queryParams(url: String): Map<String, String> {
        val query = url.substringAfter('?', "")
        if (query.isBlank()) return emptyMap()
        return query.split('&').mapNotNull { pair ->
            val k = pair.substringBefore('=', "")
            val v = pair.substringAfter('=', "")
            if (k.isBlank()) null else k.lowercase() to decode(v)
        }.toMap()
    }

    private fun decode(s: String): String =
        runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)
}
