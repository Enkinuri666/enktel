package tv.enktel.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import tv.enktel.app.AppGraph
import tv.enktel.app.R
import tv.enktel.app.data.repo.DefaultLine
import tv.enktel.app.data.repo.SetupLink
import tv.enktel.app.data.repo.SetupLink.Setup
import tv.enktel.app.i18n.LocalStrings
import tv.enktel.app.i18n.Strings
import tv.enktel.app.ui.components.AuthBackdrop
import tv.enktel.app.ui.components.FocusButton
import tv.enktel.app.ui.components.TvTextField
import tv.enktel.app.ui.theme.EnktelLive
import tv.enktel.app.ui.theme.EnktelTextDim

/**
 * First run: sign in.
 *
 * ## Two rewrites, and why this is the shape it settled on
 *
 * It began as six questions — Xtream Codes or M3U Playlist, a playlist name,
 * a server URL "(http://host:port)", a username, a password, and a
 * film-library M3U — with a protocol question first. People could not get
 * past it.
 *
 * Then it was one paste box and nothing else, which fixed that but overshot:
 * the two things an EnkTel subscriber is actually handed are a username and a
 * password, and the screen made them paste something to discover that fields
 * for those existed. A box labelled "paste your link" is not where someone
 * holding two words looks.
 *
 * So: the two fields, in front, on a branded screen — with the server already
 * known, because it is the same panel for every subscriber. Someone whose
 * provider sent a link instead opens the one extra line and pastes it there;
 * [SetupLink] reads the credentials, the host, or both out of whatever lands.
 *
 * The film-library M3U lives in Settings, where a second playlist belongs.
 */
@Composable
fun OnboardingScreen(graph: AppGraph, onDone: () -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var pasted by remember { mutableStateOf("") }
    /** The link/server row, opened by anyone not on the default panel. */
    var showLink by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val t = LocalStrings.current

    fun connect() {
        if (busy) return
        error = ""
        val setup = SetupLink.parse(pasted, username, password)
        when (setup) {
            is Setup.Unrecognised -> {
                // With the fields in front, the common failure is an empty
                // one rather than an unreadable paste — so say that, and only
                // fall back to the parser's wording when something was
                // actually typed.
                error = if (username.isBlank() || password.isBlank()) {
                    t.errNeedCredentials
                } else {
                    // The parser's own wording, which is English only: it
                    // names the shape of what was pasted, and there is no
                    // sentence to translate until it does.
                    setup.reason
                }
                return
            }
            is Setup.NeedsCredentials -> {
                error = t.errAlmostThere
                return
            }
            else -> Unit
        }
        val name = SetupLink.suggestedName(setup)
        busy = true
        progress = t.signingIn
        scope.launch {
            // The two refusals returned above, so only these two remain.
            val result = when (setup) {
                is Setup.Xtream -> graph.playlists.addXtream(name, setup.server, setup.username, setup.password)
                is Setup.M3u -> graph.playlists.addM3u(name, setup.url, "", "")
                is Setup.NeedsCredentials, is Setup.Unrecognised -> return@launch
            }
            result.fold(
                onSuccess = { profile ->
                    // Said out loud: importing a large playlist takes long
                    // enough that a silent button reads as a hang.
                    progress = t.loadingChannels
                    runCatching { graph.content.refreshAll(profile) }
                        .onFailure {
                            error = t.errSignedInNoChannels.format(it.message)
                        }
                    progress = t.loadingGuide
                    runCatching { graph.epg.refresh(profile) }
                    graph.playlists.markSynced(profile)
                    busy = false
                    progress = ""
                    onDone()
                },
                onFailure = {
                    busy = false
                    progress = ""
                    error = friendly(it.message, t)
                },
            )
        }
    }

    AuthBackdrop {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                // widthIn, not width: a fixed 560dp column on a 411dp handset
                // overflows by 149dp, and the first casualty is the fields.
                Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(R.drawable.logo_full),
                    contentDescription = "EnkTel IPTV",
                    modifier = Modifier.width(360.dp),
                )
                Spacer(Modifier.height(22.dp))

                Text(
                    t.signInTitle,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    t.signInLede,
                    color = EnktelTextDim,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))

                TvTextField(username, { username = it }, t.username)
                Spacer(Modifier.height(12.dp))
                TvTextField(password, { password = it }, t.password, password = true)

                Spacer(Modifier.height(14.dp))
                if (!showLink) {
                    // Stated rather than hidden: someone whose provider is not
                    // ours needs to know the app has assumed one, and this is
                    // the only place that assumption is visible.
                    Text(
                        t.connectingTo.format(
                            DefaultLine.server.removePrefix("https://").removePrefix("http://"),
                        ),
                        color = EnktelTextDim,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    FocusButton(
                        t.differentProvider,
                        onClick = { showLink = true },
                    )
                } else {
                    TvTextField(pasted, { pasted = it }, t.serverOrLink)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        t.serverOrLinkHelp,
                        color = EnktelTextDim,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                    )
                }

                if (error.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(error, color = EnktelLive, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
                if (progress.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(progress, color = EnktelTextDim, fontSize = 13.sp)
                }

                Spacer(Modifier.height(18.dp))
                FocusButton(
                    if (busy) t.working else t.signIn,
                    accent = true,
                    onClick = { connect() },
                )

                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.Center) {
                    // For the viewer who has the app and no line yet. Text
                    // rather than a button: a television frequently has no
                    // browser, and the address is short enough to type into
                    // the phone already in hand.
                    Text(
                        t.noAccountYet.format(tv.enktel.app.data.repo.Subscribe.SHORT_TRIAL),
                        color = EnktelTextDim,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    t.detailsStayOnDevice,
                    color = EnktelTextDim,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

/**
 * Turn a failure into something worth reading.
 *
 * `it.message` used to go straight to the screen, so a mistyped password read
 * "Panel rejected the credentials" and a bad address could produce a
 * `java.net.UnknownHostException`. Neither says what to do next, and the
 * second one is not English. Kept in step with the desktop's `friendlyError`
 * in `pc/src/lib/setupLink.ts`.
 */
internal fun friendly(message: String?, t: Strings): String {
    val m = message.orEmpty()
    return when {
        m.contains("rejected the credentials", true) -> t.errRejected
        m.contains("UnknownHost", true) || m.contains("Unable to resolve host", true) -> t.errUnknownHost
        m.contains("timeout", true) || m.contains("timed out", true) -> t.errTimeout
        m.contains("CertPath", true) || m.contains("SSL", true) || m.contains("trust anchor", true) ->
            t.errCertificate
        m.isBlank() -> t.errGeneric
        // Matched nothing: this is an exception's own text, which is English
        // whatever the app is set to. Better an untranslated sentence that
        // names the fault than a translated one that guesses at it.
        else -> m
    }
}
