package tv.enktel.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.enktel.app.encode
import tv.enktel.app.ui.components.FocusButton
import tv.enktel.app.ui.theme.EnktelBg
import tv.enktel.app.ui.theme.EnktelTextDim

/**
 * Where a shared link lands when this line does not carry what it points at.
 *
 * Someone tapped a link a friend sent them. The worst answer is the one the app
 * used to give: the detail screen returns early when its row is null, so an
 * unresolvable link opened a blank page and stayed there, and the only
 * available conclusion was that EnkTel is broken.
 *
 * So this says the two things that are actually true and useful — what the
 * title was, and that this subscription does not include it — and then offers
 * the one thing worth trying, which is a search, because providers rename
 * lines constantly and "Man Utd v Arsenal" and "Manchester United vs Arsenal"
 * are the same match under two spellings.
 */
@Composable
fun UnavailableScreen(nav: NavHostController, name: String, kind: String) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(20) {
            if (runCatching { focus.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50)
        }
    }
    val what = when (kind) {
        "channel" -> "channel"
        "series" -> "series"
        else -> "film"
    }
    Column(
        Modifier.fillMaxSize().background(EnktelBg).padding(48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Not on your playlist", color = Color.White, fontSize = 26.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            if (name.isBlank()) {
                "The $what that link points at isn't carried by the line you're signed in to."
            } else {
                "\"$name\" isn't carried by the line you're signed in to."
            },
            color = EnktelTextDim, fontSize = 15.sp,
            modifier = Modifier.widthIn(max = 560.dp),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Whoever sent it is on a subscription that includes it. If you think yours " +
                "should too, a provider often lists the same $what under a different name — " +
                "search for it before assuming it's missing.",
            color = EnktelTextDim, fontSize = 13.sp,
            modifier = Modifier.widthIn(max = 560.dp),
        )
        Spacer(Modifier.height(26.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (name.isNotBlank()) {
                FocusButton(
                    "🔎  Search for it",
                    accent = true,
                    modifier = Modifier.focusRequester(focus),
                    onClick = { nav.navigate("search?q=${encode(name)}") },
                )
                FocusButton("Home", onClick = { nav.navigate("home") })
            } else {
                FocusButton(
                    "Home",
                    accent = true,
                    modifier = Modifier.focusRequester(focus),
                    onClick = { nav.navigate("home") },
                )
            }
        }
    }
}
