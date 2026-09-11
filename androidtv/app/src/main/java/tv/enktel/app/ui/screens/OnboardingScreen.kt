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
import tv.enktel.app.data.repo.SetupLink
import tv.enktel.app.data.repo.SetupLink.Setup
import tv.enktel.app.ui.components.AuthBackdrop
import tv.enktel.app.ui.components.FocusButton
import tv.enktel.app.ui.components.TvTextField
import tv.enktel.app.ui.theme.EnktelLive
import tv.enktel.app.ui.theme.EnktelTextDim

/**
 * First run: one box, then watch.
 *
 * ## What this replaced, and why
 *
 * The old form asked six questions in order: Xtream Codes or M3U Playlist; a
 * playlist name; a server URL "(http://host:port)"; a username; a password;
 * and an optional film-library M3U with a two-line explanation of how it
 * differs from the other M3U field. The very first decision was a protocol
 * question, and the user research was unambiguous — people could not get set
 * up, and this screen is where they stopped.
 *
 * None of those are questions a subscriber can answer. What they have is
 * whatever their provider sent them, and that message already contains the
 * answers: our own welcome email carries a server, a username, a password and
 * an M3U link whose query string holds the login. So this asks for that, and
 * works the rest out — see [SetupLink].
 *
 * The two credential fields still exist, but only appear when the paste turned
 * out not to carry them. Most people never see them.
 *
 * ## What moved rather than went
 *
 * The film-library M3U is now in Settings, where a second playlist belongs. It
 * was on the first screen of a first run, above the Connect button, and it is
 * an advanced option that almost nobody has — the subscriber who needs it can
 * find it, and everyone else no longer has to decide whether they need it
 * before they have seen a channel.
 */
@Composable
fun OnboardingScreen(graph: AppGraph, onDone: () -> Unit) {
    var pasted by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    /** Set once a paste has been read and found to need a login. */
    var askForLogin by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun connect() {
        if (busy) return
        error = ""
        val setup = SetupLink.parse(pasted, username, password)
        when (setup) {
            is Setup.Unrecognised -> {
                error = setup.reason
                return
            }
            is Setup.NeedsCredentials -> {
                // Not an error, and not phrased as one: the address arrived,
                // the login did not. Ask for exactly the two things missing.
                askForLogin = true
                error = "Almost there — now your username and password."
                return
            }
            else -> Unit
        }
        val name = SetupLink.suggestedName(setup)
        busy = true
        progress = "Connecting…"
        scope.launch {
            val result = when (setup) {
                is Setup.Xtream -> graph.playlists.addXtream(name, setup.server, setup.username, setup.password)
                is Setup.M3u -> graph.playlists.addM3u(name, setup.url, "", "")
                // Both handled above; the compiler does not know that.
                else -> return@launch
            }
            result.fold(
                onSuccess = { profile ->
                    // Said out loud, because importing a large playlist takes
                    // long enough that a silent button reads as a hang — and
                    // the previous screen said nothing at all between the tap
                    // and the home screen.
                    progress = "Loading your channels…"
                    runCatching { graph.content.refreshAll(profile) }
                        .onFailure {
                            error = "Signed in, but the channel list did not load: ${it.message}"
                        }
                    progress = "Loading the TV guide…"
                    runCatching { graph.epg.refresh(profile) }
                    graph.playlists.markSynced(profile)
                    busy = false
                    progress = ""
                    onDone()
                },
                onFailure = {
                    busy = false
                    progress = ""
                    error = friendly(it.message)
                    // A refused login is the case where the two fields help,
                    // so offer them rather than leaving someone re-pasting the
                    // same line hoping for a different answer.
                    askForLogin = true
                },
            )
        }
    }

    AuthBackdrop {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                // widthIn, not width. A fixed 560dp column on a 411dp handset
                // overflows by 149dp, and the first casualty is the paste box:
                // it ran off both edges, which on the one screen that has to
                // work reads as a broken app rather than a wide layout.
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
                    "Paste the link your provider sent you",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "It's in your welcome email. You can paste the whole message — " +
                        "we'll find the parts we need.",
                    color = EnktelTextDim,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(18.dp))

                TvTextField(pasted, { pasted = it }, "Paste your link or details here")

                if (askForLogin) {
                    Spacer(Modifier.height(14.dp))
                    TvTextField(username, { username = it }, "Username")
                    Spacer(Modifier.height(10.dp))
                    TvTextField(password, { password = it }, "Password", password = true)
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
                    if (busy) "Working…" else "Start watching",
                    accent = true,
                    onClick = { connect() },
                )

                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.Center) {
                    // For the viewer who has the app and no line yet — the
                    // whole population of a fresh install that did not arrive
                    // with credentials. Text rather than a button: a
                    // television frequently has no browser, and the address is
                    // short enough to type into the phone already in hand.
                    Text(
                        "No account yet? Start a free 24-hour trial at " +
                            tv.enktel.app.data.repo.Subscribe.SHORT_TRIAL,
                        color = EnktelTextDim,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Your details stay on this device.",
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
 * The old screen printed `it.message` straight out, so a subscriber whose
 * password had a typo was shown "Panel rejected the credentials" and, on a bad
 * day, a `java.net.UnknownHostException`. Neither says what to do next, and
 * the second one is not English.
 */
private fun friendly(message: String?): String {
    val m = message.orEmpty()
    return when {
        m.contains("rejected the credentials", true) ->
            "That username or password wasn't accepted. Check them for a stray space, then try again."
        m.contains("UnknownHost", true) || m.contains("Unable to resolve host", true) ->
            "Couldn't reach that address. Check your internet, and check the address for a typo."
        m.contains("timeout", true) || m.contains("timed out", true) ->
            "The provider didn't answer in time. It may be busy — try again in a minute."
        m.contains("CertPath", true) || m.contains("SSL", true) || m.contains("trust anchor", true) ->
            "That server's security certificate couldn't be checked. Try http:// instead of https://."
        m.isBlank() -> "Couldn't connect. Check the link and try again."
        else -> m
    }
}
