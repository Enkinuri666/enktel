package tv.enktel.app.data.net

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Rewrites every outgoing request's `User-Agent` header to a media-player UA.
 *
 * Cloudflare bot-fight rules and many Xtream panel WAFs classify OkHttp's
 * default "okhttp/x.y.z" UA (and unset UAs) as bot traffic and answer with a
 * proxy-auth challenge — which the app then sees as HTTP 407, even when the
 * device has no proxy configured. Sending VLC's UA is the well-known
 * industry escape hatch for IPTV endpoints: WAF rulesets treat it as
 * "media player traffic, allow".
 *
 * Only replaces the header when the request builder didn't set an explicit
 * UA — callers that need to identify themselves (e.g. the Discord webhook
 * publisher) still can.
 *
 * [override] lets Settings (or the Panel Doctor's auto-tune) present a
 * different client entirely. Some panels allow their own app's agent and
 * nothing else, so the escape hatch has to be configurable rather than fixed.
 * Read through a supplier on every request so a change takes effect without
 * rebuilding the OkHttp client.
 */
class UserAgentInterceptor(
    private val ua: String,
    private val override: () -> String = { "" },
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (!original.header("User-Agent").isNullOrBlank()) return chain.proceed(original)
        val effective = override().takeIf { it.isNotBlank() } ?: ua
        val patched = original.newBuilder()
            .header("User-Agent", effective)
            .build()
        return chain.proceed(patched)
    }
}
