package tv.enktel.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import tv.enktel.app.BuildConfig
import tv.enktel.app.ui.components.FocusButton
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelSurface
import tv.enktel.app.ui.theme.EnktelTextDim
import java.net.URLEncoder

/**
 * Upgrade landing:
 *  - Mobile flavor: opens the upgrade URL in the phone's browser (users tap
 *    Sign Up there without leaving their comfort zone). Pops the back stack
 *    when they return so the app doesn't get stuck on an empty screen.
 *  - TV flavor: shows a full-screen QR pointing at the same URL so the user
 *    can scan with their phone. Way faster than typing the URL on a
 *    D-pad keyboard.
 *
 * The QR image comes from api.qrserver.com — a stable free QR generator we've
 * used in other TV apps. Coil loads it, cache-friendly. If the network drops
 * we still show the URL text so the user can type it in manually.
 */
@Composable
fun UpgradeScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val isMobile = BuildConfig.FLAVOR == "mobile"
    val upgradeUrl = BuildConfig.EAGLE_UPGRADE_URL

    if (isMobile) {
        // Fire the intent once, then bounce back so we don't get stuck on a
        // blank screen. Handling this in a LaunchedEffect avoids re-firing
        // when the user returns to the app after finishing signup.
        LaunchedEffect(Unit) {
            runCatching {
                ctx.startActivity(
                    Intent(Intent.ACTION_VIEW, upgradeUrl.toUri())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            nav.popBackStack()
        }
        return
    }

    // ── TV path: big QR ───────────────────────────────────────────────
    val qrImageUrl = "https://api.qrserver.com/v1/create-qr-code/?" +
        "size=520x520&margin=10&data=" + URLEncoder.encode(upgradeUrl, "UTF-8")

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .width(680.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(EnktelSurface.copy(alpha = 0.85f))
                .border(1.dp, EnktelBlue.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                .padding(horizontal = 40.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Upgrade your EnkTel account",
                color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Scan the QR with your phone to buy a full-year subscription.",
                color = EnktelTextDim, fontSize = 12.sp,
            )
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier
                    // A fixed 360 dp QR is wider than the usable width of most
                    // phones in portrait once padding is taken off, so it was
                    // clipped on exactly the devices meant to scan it. Cap it
                    // and let it shrink instead.
                    .widthIn(max = 360.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White)
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = qrImageUrl,
                    contentDescription = "Upgrade QR",
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Or open this URL in any browser:",
                color = EnktelTextDim, fontSize = 12.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                upgradeUrl,
                color = EnktelBlue, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(20.dp))
            FocusButton("Done", accent = true, onClick = { nav.popBackStack() })
        }
    }
}
