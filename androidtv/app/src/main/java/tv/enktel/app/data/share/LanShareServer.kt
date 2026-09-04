package tv.enktel.app.data.share

import java.io.BufferedOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * A small HTTP server that hands downloaded files to a browser on the same
 * network.
 *
 * Written directly on [ServerSocket] rather than pulled in as a library. What
 * it has to do is narrow — three routes, one of them a file with byte ranges —
 * and a general-purpose server would bring a great deal of surface that this
 * has no use for, on a socket listening in someone's home.
 *
 * Everything it will serve is passed to [start] and cannot change afterwards
 * without stopping it. There is no route that takes a path.
 */
class LanShareServer {

    /** One file the viewer chose to make available. */
    data class Shared(
        val token: String,
        val filename: String,
        val size: Long,
        /** Opens the bytes. Null when the file has gone since it was shared. */
        val open: () -> InputStream?,
    )

    /** What the viewer needs in order to fetch it. */
    data class Started(val ip: String, val port: Int, val pin: String) {
        val url: String get() = LanShare.shareUrl(ip, port)
    }

    private var socket: ServerSocket? = null
    private val pool = Executors.newFixedThreadPool(4)
    private val files = ConcurrentHashMap<String, Shared>()

    @Volatile private var pin: String = ""
    @Volatile private var session: String = ""

    /**
     * Wrong PINs, counted.
     *
     * Six digits is a million guesses, which a script on the same network
     * could work through. Ten wrong answers and the server stops accepting
     * any: the viewer restarts it and gets a new PIN, which is a two-second
     * inconvenience for them and a dead end for anything automated.
     */
    private val wrongPins = AtomicInteger(0)
    @Volatile private var lockedOut = false

    val running: Boolean get() = socket?.isClosed == false

    /** Bind and begin serving [shared]. Returns null when the port is taken. */
    fun start(ip: String, shared: List<Shared>, port: Int = LanShare.DEFAULT_PORT): Started? {
        stop()
        files.clear()
        shared.forEach { files[it.token] = it }
        pin = LanShare.newPin()
        session = LanShare.newToken()
        wrongPins.set(0)
        lockedOut = false

        val s = try {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(port), 16)
            }
        } catch (_: Throwable) {
            return null
        }
        socket = s
        thread(name = "lan-share-accept", isDaemon = true) {
            while (!s.isClosed) {
                val client = try { s.accept() } catch (_: Throwable) { break }
                try {
                    pool.execute { handle(client) }
                } catch (_: Throwable) {
                    runCatching { client.close() }
                }
            }
        }
        return Started(ip, port, pin)
    }

    fun stop() {
        runCatching { socket?.close() }
        socket = null
        files.clear()
        // Cleared so a stopped server cannot be talked to by a request that
        // was already in flight.
        pin = ""
        session = ""
    }

    // ── requests ───────────────────────────────────────────────────────

    private fun handle(client: Socket) {
        client.use { sock ->
            sock.soTimeout = 20_000
            val input = sock.getInputStream().buffered()
            val out = BufferedOutputStream(sock.getOutputStream(), 64 * 1024)

            val requestLine = readLine(input) ?: return
            val parts = requestLine.split(' ')
            if (parts.size < 2) return
            val method = parts[0].uppercase()
            val target = parts[1]

            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                val i = line.indexOf(':')
                if (i > 0) headers[line.substring(0, i).trim().lowercase()] =
                    line.substring(i + 1).trim()
            }

            val path = target.substringBefore('?')
            val query = target.substringAfter('?', "")

            if (lockedOut) {
                send(out, 429, "Too Many Requests", page("Too many wrong PINs. Restart sending on the phone to get a new one."))
                return
            }

            // The PIN arrives once, as a form post; after that a session
            // cookie carries it so it is not sitting in every URL.
            if (method == "POST" && path == "/") {
                val length = headers["content-length"]?.toIntOrNull() ?: 0
                val body = readBody(input, length)
                val supplied = formValue(body, "pin")
                if (LanShare.pinMatches(supplied, pin)) {
                    sendRedirect(out, "/", session)
                } else {
                    if (wrongPins.incrementAndGet() >= MAX_WRONG_PINS) lockedOut = true
                    send(out, 401, "Unauthorized", loginPage("That PIN was not right."))
                }
                return
            }

            val authorised = headers["cookie"].orEmpty().contains("$COOKIE=$session") && session.isNotEmpty()
            if (!authorised) {
                send(out, 401, "Unauthorized", loginPage(null))
                return
            }

            when {
                path == "/" -> send(out, 200, "OK", listPage())
                path.startsWith("/f/") -> serveFile(out, path.removePrefix("/f/"), headers, query)
                else -> send(out, 404, "Not Found", page("No such thing here."))
            }
        }
    }

    private fun serveFile(
        out: BufferedOutputStream,
        token: String,
        headers: Map<String, String>,
        @Suppress("UNUSED_PARAMETER") query: String,
    ) {
        // The only lookup there is. A token that is not in the map is a 404
        // whatever it says, so there is nothing to traverse.
        val file = files[token]
        if (file == null) {
            send(out, 404, "Not Found", page("That file is no longer being shared."))
            return
        }
        val stream = file.open()
        if (stream == null) {
            send(out, 410, "Gone", page("That file has been deleted from the phone."))
            return
        }

        val range = LanShare.parseRange(headers["range"], file.size)
        if (headers["range"] != null && range == null && file.size > 0) {
            // Refused rather than answered with the whole file: a resume that
            // silently restarts produces a corrupt copy that looks complete.
            stream.close()
            sendHead(out, 416, "Range Not Satisfiable", emptyList(), 0)
            out.flush()
            return
        }

        val start = range?.first ?: 0L
        val end = range?.last ?: (file.size - 1)
        val length = (end - start + 1).coerceAtLeast(0)

        val extra = mutableListOf(
            "Content-Type: ${LanShare.contentType(file.filename)}",
            "Content-Disposition: attachment; filename*=UTF-8''${URLEncoder.encode(file.filename, "UTF-8").replace("+", "%20")}",
            "Accept-Ranges: bytes",
        )
        if (range != null) extra += "Content-Range: bytes $start-$end/${file.size}"

        sendHead(out, if (range != null) 206 else 200, if (range != null) "Partial Content" else "OK", extra, length)

        stream.use { s ->
            if (start > 0) skipFully(s, start)
            val buf = ByteArray(256 * 1024)
            var remaining = length
            while (remaining > 0) {
                val want = minOf(buf.size.toLong(), remaining).toInt()
                val n = s.read(buf, 0, want)
                if (n <= 0) break
                out.write(buf, 0, n)
                remaining -= n
            }
        }
        runCatching { out.flush() }
    }

    /** `skip` may return short for any reason; a loop is the only correct read. */
    private fun skipFully(s: InputStream, count: Long) {
        var left = count
        while (left > 0) {
            val n = s.skip(left)
            if (n <= 0) {
                // Some streams refuse to skip; fall back to reading through.
                val buf = ByteArray(64 * 1024)
                val want = minOf(buf.size.toLong(), left).toInt()
                val r = s.read(buf, 0, want)
                if (r <= 0) return
                left -= r
            } else {
                left -= n
            }
        }
    }

    // ── plumbing ───────────────────────────────────────────────────────

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val c = input.read()
            if (c < 0) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) return sb.toString().removeSuffix("\r")
            if (sb.length > 8192) return null
            sb.append(c.toChar())
        }
    }

    private fun readBody(input: InputStream, length: Int): String {
        if (length <= 0 || length > 4096) return ""
        val buf = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(buf, read, length - read)
            if (n <= 0) break
            read += n
        }
        return String(buf, 0, read)
    }

    private fun formValue(body: String, key: String): String =
        body.split('&')
            .firstOrNull { it.substringBefore('=') == key }
            ?.substringAfter('=', "")
            ?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
            .orEmpty()

    private fun sendHead(
        out: BufferedOutputStream,
        code: Int,
        reason: String,
        extra: List<String>,
        contentLength: Long,
    ) {
        val head = buildString {
            append("HTTP/1.1 $code $reason\r\n")
            append("Content-Length: $contentLength\r\n")
            append("Connection: close\r\n")
            // Nothing here should ever be framed, sniffed or cached.
            append("X-Content-Type-Options: nosniff\r\n")
            append("X-Frame-Options: DENY\r\n")
            append("Cache-Control: no-store\r\n")
            extra.forEach { append(it).append("\r\n") }
            append("\r\n")
        }
        out.write(head.toByteArray())
    }

    private fun send(out: BufferedOutputStream, code: Int, reason: String, html: String) {
        val body = html.toByteArray()
        sendHead(out, code, reason, listOf("Content-Type: text/html; charset=utf-8"), body.size.toLong())
        out.write(body)
        out.flush()
    }

    private fun sendRedirect(out: BufferedOutputStream, location: String, sessionId: String) {
        sendHead(
            out, 303, "See Other",
            listOf(
                "Location: $location",
                // HttpOnly so no script can read it; SameSite=Strict so
                // another page cannot make the browser use it.
                "Set-Cookie: $COOKIE=$sessionId; Path=/; HttpOnly; SameSite=Strict",
            ),
            0,
        )
        out.flush()
    }

    // ── pages ──────────────────────────────────────────────────────────

    private fun escape(s: String) = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#39;")

    private fun page(message: String) = """
        <!doctype html><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>EnkTel</title>$STYLE
        <h1>EnkTel</h1><p>${escape(message)}</p>
    """.trimIndent()

    private fun loginPage(error: String?) = """
        <!doctype html><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>EnkTel</title>$STYLE
        <h1>EnkTel</h1>
        <p>Enter the PIN shown on your phone.</p>
        ${if (error != null) "<p class=\"err\">${escape(error)}</p>" else ""}
        <form method="post" action="/">
          <input name="pin" inputmode="numeric" autocomplete="off" pattern="[0-9]*"
                 maxlength="6" autofocus placeholder="000000">
          <button type="submit">Continue</button>
        </form>
    """.trimIndent()

    private fun listPage(): String {
        val rows = files.values.sortedBy { it.filename }.joinToString("") { f ->
            """<li><a href="/f/${f.token}">${escape(f.filename)}</a>
               <span class="sz">${humanSize(f.size)}</span></li>"""
        }
        val body = if (files.isEmpty()) "<p>Nothing is being shared right now.</p>"
        else "<ul>$rows</ul><p class=\"sz\">Click a title to save it. Large files resume if the connection drops.</p>"
        return """
            <!doctype html><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>EnkTel downloads</title>$STYLE
            <h1>EnkTel downloads</h1>$body
        """.trimIndent()
    }

    private fun humanSize(b: Long): String {
        if (b <= 0) return ""
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var v = b.toDouble(); var u = 0
        while (v >= 1024 && u < units.lastIndex) { v /= 1024; u++ }
        return if (u == 0) "$b B" else "%.1f %s".format(v, units[u])
    }

    companion object {
        private const val COOKIE = "enktel_share"
        private const val MAX_WRONG_PINS = 10

        private val STYLE = """
            <style>
              body{background:#0b1020;color:#fff;font:16px/1.5 system-ui,sans-serif;margin:0;padding:32px}
              h1{font-size:20px;margin:0 0 16px}
              a{color:#7cc4ff}
              ul{list-style:none;padding:0;margin:0}
              li{padding:12px 0;border-bottom:1px solid #ffffff1f;display:flex;
                 justify-content:space-between;gap:16px;align-items:baseline}
              .sz{color:#9aa4bf;font-size:13px}
              .err{color:#ff8080}
              input,button{font-size:18px;padding:10px 14px;border-radius:8px;border:1px solid #ffffff33;
                           background:#141b33;color:#fff}
              button{background:#2563eb;border-color:#2563eb;cursor:pointer}
            </style>
        """.trimIndent()
    }
}
