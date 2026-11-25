package tv.enktel.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import tv.enktel.app.AppGraph
import tv.enktel.app.data.TimeFormat
import tv.enktel.app.data.db.Profile
import tv.enktel.app.data.bool
import tv.enktel.app.data.get
import tv.enktel.app.data.int
import tv.enktel.app.data.long
import tv.enktel.app.data.str
import tv.enktel.app.ui.components.ConfirmDialog
import tv.enktel.app.ui.components.FocusButton
import tv.enktel.app.ui.components.LocalToaster
import tv.enktel.app.ui.theme.EnktelLive
import tv.enktel.app.ui.theme.EnktelOk
import tv.enktel.app.ui.theme.EnktelTextDim

/**
 * v1.89 Settings ▸ Accounts: the active line at a glance — portal, user,
 * status, expiry and device slots in use — with Refresh Playlist, Check
 * Connection and Log Out underneath, as in the redesign.
 *
 * Status and slots come live from the panel (Xtream `user_info`); an M3U line
 * has no such endpoint, so those rows say so instead of guessing.
 */
@Composable
fun AccountsPanel(graph: AppGraph, profile: Profile?, nav: NavHostController) {
    val p = profile ?: run {
        Text("No account yet — add a playlist under Playlists.", color = EnktelTextDim, fontSize = 15.sp)
        return
    }
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    var checkTick by remember { mutableIntStateOf(0) }
    var active by remember(p.id) { mutableIntStateOf(-1) }
    var max by remember(p.id) { mutableIntStateOf(p.maxConnections) }
    var expires by remember(p.id) { mutableLongStateOf(p.expiresAt) }
    // null = still checking. Reachability is "did the panel answer", which is
    // what "Check Connection" actually asks — kept apart from whether the
    // credentials authorised, so a valid account that answers never reads as
    // "Unreachable".
    var reachable by remember(p.id) { mutableStateOf<Boolean?>(null) }
    var authorized by remember(p.id) { mutableStateOf(false) }
    // The panel's own account state: "Active", "Expired", "Banned", "Disabled".
    var statusLabel by remember(p.id) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }

    LaunchedEffect(p.id, checkTick) {
        if (p.kind != "xtream") return@LaunchedEffect
        reachable = null
        runCatching {
            val ui = graph.xtream.login(p).get("user_info")
            if (ui == null) {
                // The panel answered, but not with a user_info block — an HTML
                // error page or a login rejection. Reachable, not authorised.
                reachable = true
                authorized = false
                statusLabel = ""
                return@runCatching
            }
            // The panel responded with account data: it is reachable.
            reachable = true
            ui.int("active_cons")?.let { active = it }
            ui.int("max_connections")?.let { max = it }
            ui.long("exp_date")?.let { expires = it * 1000L }
            // `auth` comes back as 1, "1" or even a JSON boolean `true`
            // depending on the panel — bool() handles all three, where the old
            // int()==1 silently failed on the boolean form and showed
            // "Unreachable" for a perfectly good account. `status` is the
            // authoritative account state and a second, independent signal.
            statusLabel = ui.str("status").orEmpty()
            authorized = ui.bool("auth") ||
                statusLabel.equals("Active", ignoreCase = true) ||
                expires > System.currentTimeMillis()
        }.onFailure { reachable = false }
    }

    val statusText = when {
        p.kind != "xtream" -> "Not reported by M3U"
        reachable == null -> "Checking…"
        reachable == false -> "Unreachable"
        authorized || statusLabel.equals("Active", ignoreCase = true) -> "Online"
        statusLabel.isNotBlank() -> statusLabel
        else -> "Credentials rejected"
    }
    val statusColor = when {
        reachable == null -> Color.White
        reachable == false -> EnktelLive
        authorized || statusLabel.equals("Active", ignoreCase = true) -> EnktelOk
        else -> tv.enktel.app.ui.theme.EnktelWarn
    }

    Column(Modifier.fillMaxWidth()) {
        Text("Accounts", color = Color.White.copy(alpha = 0.85f), fontSize = 26.sp)
        Spacer(Modifier.height(14.dp))
        AccountRow("Active Portal", "${p.name} (${if (p.kind == "xtream") "Xtream Codes API" else "M3U"})")
        AccountRow("Username", p.username.ifBlank { "—" })
        AccountRow("Status", statusText, valueColor = statusColor)
        AccountRow(
            "Subscription Expiry",
            if (expires > 0) TimeFormat.format("dd/MM/yyyy", expires) else "Unlimited / not reported",
        )
        AccountRow(
            "Multi-Device Status",
            when {
                p.kind != "xtream" -> "Not reported by M3U"
                max > 0 && active >= 0 -> "$active/$max Used"
                max > 0 -> "–/$max"
                else -> "—"
            },
        )
        Spacer(Modifier.height(18.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FocusButton(
                if (busy) "Refreshing…" else "Refresh Playlist",
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (busy) return@FocusButton
                    busy = true
                    scope.launch {
                        runCatching { graph.content.refreshAll(p) }.fold(
                            onSuccess = { toaster.success("Playlist refreshed") },
                            onFailure = { toaster.error("Refresh failed: ${it.message ?: "unknown error"}") },
                        )
                        busy = false
                    }
                },
            )
            FocusButton(
                "Check Connection",
                modifier = Modifier.fillMaxWidth(),
                onClick = { checkTick++; toaster.info("Checking ${p.name}…") },
            )
            FocusButton(
                "Log Out",
                modifier = Modifier.fillMaxWidth(),
                onClick = { confirmLogout = true },
            )
        }
    }

    if (confirmLogout) {
        ConfirmDialog(
            title = "Log out of ${p.name}?",
            message = "This removes the account and its downloaded catalogue from this device. " +
                "Recordings and downloads stay. You can add it again from the start screen.",
            confirmLabel = "Log Out",
            onConfirm = {
                confirmLogout = false
                scope.launch {
                    graph.playlists.delete(p.id)
                    val next = runCatching { graph.playlists.profiles.first().firstOrNull() }.getOrNull()
                    if (next != null) {
                        graph.playlists.switchTo(next.id)
                        nav.navigate("home") { popUpTo(0) }
                    } else {
                        nav.navigate("onboarding") { popUpTo(0) }
                    }
                }
            },
            onDismiss = { confirmLogout = false },
        )
    }
}

@Composable
private fun AccountRow(label: String, value: String, valueColor: Color = Color.White) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Color.White, fontSize = 18.sp, modifier = Modifier.weight(1f))
            Text(value, color = valueColor, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.10f)))
    }
}
