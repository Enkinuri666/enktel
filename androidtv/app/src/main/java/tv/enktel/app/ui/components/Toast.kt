package tv.enktel.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelLive
import tv.enktel.app.ui.theme.EnktelOk

enum class ToastLevel { INFO, SUCCESS, ERROR }

class Toaster {
    var message by mutableStateOf<Pair<String, ToastLevel>?>(null)
        private set

    fun info(text: String) { message = text to ToastLevel.INFO }
    fun success(text: String) { message = text to ToastLevel.SUCCESS }
    fun error(text: String) { message = text to ToastLevel.ERROR }
    fun clear() { message = null }
}

val LocalToaster = compositionLocalOf { Toaster() }

@Composable
fun ToastHost(content: @Composable () -> Unit) {
    val toaster = remember { Toaster() }
    CompositionLocalProvider(LocalToaster provides toaster) {
        Box(Modifier.fillMaxSize()) {
            content()
            AnimatedVisibility(
                visible = toaster.message != null,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp),
            ) {
                val msg = toaster.message
                if (msg != null) {
                    val dot = when (msg.second) {
                        ToastLevel.SUCCESS -> EnktelOk
                        ToastLevel.ERROR -> EnktelLive
                        ToastLevel.INFO -> EnktelBlue
                    }
                    Row(
                        Modifier
                            .background(Color.Black.copy(0.85f), RoundedCornerShape(24.dp))
                            .padding(PaddingValues(horizontal = 22.dp, vertical = 12.dp)),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.background(dot, RoundedCornerShape(percent = 50)).padding(6.dp))
                        Text(msg.first, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            LaunchedEffect(toaster.message) {
                if (toaster.message != null) {
                    delay(3200)
                    toaster.clear()
                }
            }
        }
    }
}
