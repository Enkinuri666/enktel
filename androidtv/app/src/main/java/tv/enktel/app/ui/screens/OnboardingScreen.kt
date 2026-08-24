package tv.enktel.app.ui.screens

import androidx.core.net.toUri
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.Text
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import tv.enktel.app.AppGraph
import tv.enktel.app.R
import tv.enktel.app.ui.components.AuthBackdrop
import tv.enktel.app.ui.components.FocusButton
import tv.enktel.app.ui.components.TvTextField
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelLive
import tv.enktel.app.ui.theme.EnktelOk
import tv.enktel.app.ui.theme.EnktelTextDim

@Composable
fun OnboardingScreen(graph: AppGraph, onDone: () -> Unit) {
    var mode by remember { mutableStateOf("xtream") }
    var name by remember { mutableStateOf("My Playlist") }
    // Prefilled so the common case is two fields, not three.
    var server by remember { mutableStateOf(tv.enktel.app.data.repo.DefaultLine.server) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var m3uUrl by remember { mutableStateOf("") }
    var epgUrl by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var trialBusy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var trialMessage by remember { mutableStateOf("") }
    var showTrialExpired by remember { mutableStateOf(false) }
    val trialUsed by graph.settings.trialUsed.collectAsStateWithLifecycle(initialValue = false)
    val trialExpiresAt by graph.settings.trialExpiresAt.collectAsStateWithLifecycle(initialValue = 0L)
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    // Sign-in was a bare Box over whatever happened to be behind it, so the
    // first surface anyone saw was the flattest one in the app while every
    // screen past it is layered artwork. AuthBackdrop draws the same brand
    // gradients the rest of the UI uses, in slow motion.
    AuthBackdrop {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(560.dp).verticalScroll(rememberScrollState()).padding(vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.logo_full),
                contentDescription = "EnkTel IPTV",
                modifier = Modifier.width(360.dp),
            )
            Spacer(Modifier.height(20.dp))

            // ── Free 24 h trial CTA ─────────────────────────────────────
            // Highest-visibility action on the login screen for anyone
            // arriving without credentials. Calls the Eagle 4K trial API,
            // logs the user in on success, kicks off the initial content
            // sync and drops them on Home — no form to fill.
            val trialExpired = trialUsed && trialExpiresAt > 0 && trialExpiresAt < System.currentTimeMillis()
            TrialCard(
                busy = trialBusy,
                message = trialMessage,
                expired = trialExpired,
                onStart = {
                    if (trialExpired) {
                        showTrialExpired = true
                        return@TrialCard
                    }
                    if (trialUsed) {
                        showTrialExpired = true
                        return@TrialCard
                    }
                    if (trialBusy || busy) return@TrialCard
                    trialBusy = true; error = ""; trialMessage = "Contacting Eagle 4K…"
                    scope.launch {
                        val client = graph.trialClient
                        val credsResult = client.createTrial(ctx)
                        credsResult.fold(
                            onSuccess = { creds ->
                                trialMessage = "Signing in…"
                                graph.playlists.addTrial(creds).fold(
                                    onSuccess = { profile ->
                                        trialMessage = "Loading catalogue…"
                                        runCatching { graph.content.refreshAll(profile) }
                                        runCatching { graph.epg.refresh(profile) }
                                        graph.playlists.markSynced(profile)
                                        trialBusy = false
                                        onDone()
                                    },
                                    onFailure = {
                                        trialBusy = false
                                        trialMessage = ""
                                        error = it.message ?: "Could not sign in with trial credentials"
                                    },
                                )
                            },
                            onFailure = {
                                trialBusy = false
                                trialMessage = ""
                                if (it is tv.enktel.app.data.net.TrialAlreadyUsedException) {
                                    // The server knows this device has had its
                                    // trial even though a reinstall wiped the
                                    // local flag. Show the offer rather than an
                                    // error the user can only stare at, and
                                    // write the flag back so the next tap does
                                    // not need a round trip to find out.
                                    scope.launch { graph.settings.setTrialUsed(true) }
                                    showTrialExpired = true
                                } else {
                                    error = it.message ?: "Trial signup failed"
                                }
                            },
                        )
                    }
                },
            )
            Spacer(Modifier.height(20.dp))

            tv.enktel.app.ui.components.ChipRowLabel("Playlist type")
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tv.enktel.app.ui.components.GlassChip("Xtream Codes", selected = mode == "xtream", onClick = { mode = "xtream" })
                tv.enktel.app.ui.components.GlassChip("M3U Playlist", selected = mode == "m3u", onClick = { mode = "m3u" })
            }
            Spacer(Modifier.height(20.dp))
            TvTextField(name, { name = it }, "Playlist name")
            Spacer(Modifier.height(12.dp))
            if (mode == "xtream") {
                TvTextField(server, { server = it }, "Server URL (http://host:port)")
                Spacer(Modifier.height(12.dp))
                TvTextField(username, { username = it }, "Username")
                Spacer(Modifier.height(12.dp))
                TvTextField(password, { password = it }, "Password", password = true)
            } else {
                TvTextField(m3uUrl, { m3uUrl = it }, "M3U URL")
                Spacer(Modifier.height(12.dp))
                TvTextField(epgUrl, { epgUrl = it }, "EPG / XMLTV URL (optional)")
            }
            Spacer(Modifier.height(8.dp))
            if (error.isNotBlank()) {
                Text(error, color = EnktelLive, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
            }
            FocusButton(
                if (busy) "Connecting…" else "Connect & Import",
                accent = true,
                onClick = onClick@{
                    if (busy) return@onClick
                    busy = true; error = ""
                    scope.launch {
                        val result = if (mode == "xtream") {
                            graph.playlists.addXtream(name, server, username, password)
                        } else {
                            graph.playlists.addM3u(name, m3uUrl, epgUrl)
                        }
                        result.fold(
                            onSuccess = { profile ->
                                runCatching { graph.content.refreshAll(profile) }
                                    .onFailure { error = "Imported profile but sync failed: ${it.message}" }
                                runCatching { graph.epg.refresh(profile) }
                                graph.playlists.markSynced(profile)
                                busy = false
                                onDone()
                            },
                            onFailure = {
                                busy = false
                                error = it.message ?: "Could not connect"
                            },
                        )
                    }
                },
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Your credentials stay on this device.",
                color = EnktelTextDim,
                fontSize = 12.sp,
            )
        }
    }

    if (showTrialExpired) {
        // This screen runs before a profile exists, so it is outside the main
        // NavHost and cannot route to the Upgrade screen. The dialog carries
        // the checkout itself instead of telling the user to go and find it —
        // "go to Settings > Upgrade Account after logging in" was advice you
        // could not follow, since the whole reason you are reading it is that
        // you have nothing to log in with.
        val checkoutUrl = tv.enktel.app.BuildConfig.EAGLE_UPGRADE_URL
        val isMobile = tv.enktel.app.BuildConfig.FLAVOR == "mobile"
        tv.enktel.app.ui.components.ConfirmDialog(
            title = "Free Trial Ended",
            message = buildString {
                append("Your 24-hour free trial has finished, and a device only gets one.\n\n")
                append("Keep everything you have been watching — live TV, movies and series ")
                append("in 4K — with 12 months of full access for \$99 USD, paid once. ")
                append("No subscription, nothing to cancel.\n\n")
                append("Your account is created and activated automatically as soon as ")
                append("PayPal confirms the payment, and your login details are emailed ")
                append("to you.\n\n")
                // A sideloaded Fire TV Stick frequently has no browser, so the
                // button below may have nowhere to go. Printing the address
                // means the sale is still reachable from a phone.
                if (!isMobile) append("Open this on your phone:\n$checkoutUrl")
                else append("Already subscribed? Log in with your credentials below.")
            },
            confirmLabel = if (isMobile) "Get 12 Months — \$99 USD" else "OK",
            onConfirm = {
                showTrialExpired = false
                if (isMobile) {
                    runCatching {
                        ctx.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                checkoutUrl.toUri(),
                            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
            },
            onDismiss = { showTrialExpired = false },
        )
    }
    }
}

/**
 * Prominent free-trial CTA card at the top of the login screen. Owns its own
 * busy indicator + status line so callers only see start/finish.
 */
@Composable
private fun TrialCard(busy: Boolean, message: String, expired: Boolean = false, onStart: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(EnktelBlue.copy(alpha = 0.12f))
            .border(1.dp, EnktelBlue.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (expired) "⏰" else "🎁", fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                if (expired) "Trial expired" else "24-hour free trial",
                color = if (expired) EnktelLive else Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (expired) "Your free trial has ended. Upgrade for \$99/year to continue."
            else "Full access to live TV, movies & series — no credit card required.",
            color = EnktelTextDim,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(12.dp))
        FocusButton(
            when {
                expired -> "Upgrade — \$99/year"
                busy -> "Setting up your trial…"
                else -> "Start free trial"
            },
            accent = true,
            onClick = onStart,
        )
        if (busy && message.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(message, color = EnktelOk, fontSize = 11.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (expired) "Secure PayPal checkout — your account activates automatically."
            else "Trial ends automatically after 24 hours. You'll see an upgrade prompt in Settings.",
            color = EnktelTextDim, fontSize = 11.sp,
        )
    }
}
