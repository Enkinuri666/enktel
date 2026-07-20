package tv.enktel.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import tv.enktel.app.ui.theme.EnktelSurfaceHigh

data class ContextAction(val label: String, val onClick: () -> Unit, val accent: Boolean = false)

/**
 * Center-screen popup with a title and a stack of buttons. Used everywhere long-press or
 * MENU is a natural gesture — favorites, watchlist, hide, info, etc. — so users never have
 * to leave the current screen to trigger these actions.
 */
@Composable
fun ContextMenu(title: String, actions: List<ContextAction>, onDismiss: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(320.dp)
                .background(EnktelSurfaceHigh, RoundedCornerShape(12.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            actions.forEach { a ->
                FocusButton(a.label, accent = a.accent, onClick = a.onClick, modifier = Modifier.fillMaxSize().width(280.dp))
            }
            FocusButton("Close", onClick = onDismiss, modifier = Modifier.fillMaxSize().width(280.dp))
        }
    }
}
