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
 */
class UserAgentInterceptor(private val ua: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (!original.header("User-Agent").isNullOrBlank()) return chain.proceed(original)
        val patched = original.newBuilder()
            .header("User-Agent", ua)
            .build()
        return chain.proceed(patched)
    }
}
