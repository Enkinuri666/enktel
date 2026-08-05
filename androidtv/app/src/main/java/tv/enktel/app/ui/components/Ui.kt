package tv.enktel.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.ClickableSurfaceDefaults
import coil.compose.AsyncImage
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelFocusGlow
import tv.enktel.app.ui.theme.EnktelFocusGlowRadius
import tv.enktel.app.ui.theme.EnktelFocusRingWidth
import tv.enktel.app.ui.theme.EnktelFocusScale
import tv.enktel.app.ui.theme.EnktelPurple
import tv.enktel.app.ui.theme.EnktelSurfaceHigh
import tv.enktel.app.ui.theme.EnktelTextDim

/**
 * TV-material surfaces only react to DPAD select; on touchscreens (phones, tablets,
 * touch-enabled boxes) taps land nowhere. Attach this alongside Surface(onClick) so
 * both input methods work.
 */
fun Modifier.tapClick(onClick: () -> Unit): Modifier =
    pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) }

@Composable
fun FocusButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.tapClick(onClick),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (accent) EnktelBlue.copy(alpha = 0.25f) else EnktelSurfaceHigh,
            focusedContainerColor = EnktelBlue,
            focusedContentColor = Color.White,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/**
 * Netflix-style pill chip for filter rows. Sits lower than FocusButton visually
 * (used for tag/genre/decade selection, not primary CTAs) and adds a hairline
 * border in the selected state so a chosen filter reads at a glance.
 */
@Composable
fun GlassChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = EnktelBlue,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.tapClick(onClick),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) accent.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.06f),
            focusedContainerColor = accent,
            contentColor = if (selected) Color.White else EnktelTextDim,
            focusedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            border = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (selected) accent.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.12f),
                ),
                shape = RoundedCornerShape(20.dp),
            ),
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White),
                shape = RoundedCornerShape(20.dp),
            ),
        ),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/** Small uppercase heading used above chip rows. */
@Composable
fun ChipRowLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        color = EnktelTextDim,
        fontSize = 10.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.5.sp,
        modifier = modifier,
    )
}

@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    Column(modifier.fillMaxWidth()) {
        Text(label, color = EnktelTextDim, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
            cursorBrush = SolidColor(EnktelBlue),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) },
                onDone = { focusManager.moveFocus(FocusDirection.Down) },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                // BasicTextField consumes DPAD on TV, trapping focus after text entry —
                // hand vertical presses back to the focus system explicitly.
                .onPreviewKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (ev.key.nativeKeyCode) {
                        AndroidKeyEvent.KEYCODE_DPAD_DOWN -> { focusManager.moveFocus(FocusDirection.Down); true }
                        AndroidKeyEvent.KEYCODE_DPAD_UP -> { focusManager.moveFocus(FocusDirection.Up); true }
                        else -> false
                    }
                }
                .background(EnktelSurfaceHigh, RoundedCornerShape(8.dp))
                .border(
                    2.dp,
                    if (focused) EnktelBlue else Color.Transparent,
                    RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

@Composable
fun PosterCard(
    title: String,
    imageUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    wide: Boolean = false,
    /** TMDB id, when the catalogue row has been enriched. Non-zero opts this
     *  card into the hover auto-trailer; 0 leaves it a plain poster. */
    tmdbId: Long = 0,
    isSeries: Boolean = false,
) {
    val w = if (wide) 240.dp else 150.dp
    val h = if (wide) 135.dp else 210.dp
    var focused by remember { mutableStateOf(false) }
    // v1.28.0 — report focus + poster URL to the enclosing FocusedPosterState
    // (if provided), so the AmbilightGlow backdrop can crossfade to whichever
    // poster the user rests on. Null when no screen has installed the state,
    // so the modifier degrades to a no-op elsewhere in the app.
    val focusedPoster = LocalFocusedPoster.current

    // Depth. The card previously drew on a transparent container with only a
    // focus ring, so it sat perfectly flat against the background — the ring
    // told you where focus was, but nothing lifted off the page, which is what
    // made rails read as a grid of stickers rather than physical cards.
    //
    // Both the elevation and the lift are animated rather than switched. A
    // shadow that appears instantly reads as a rendering artefact; one that
    // grows over ~180 ms reads as the card rising to meet you. The easing is
    // deliberately asymmetric — quick to lift, slower to settle — because that
    // is how weight behaves.
    val elevation by animateDpAsState(
        targetValue = if (focused) 18.dp else 3.dp,
        animationSpec = tween(durationMillis = if (focused) 180 else 260),
        label = "posterElevation",
    )
    val lift by animateDpAsState(
        targetValue = if (focused) (-6).dp else 0.dp,
        animationSpec = tween(durationMillis = if (focused) 180 else 260),
        label = "posterLift",
    )

    Surface(
        onClick = { NavSounds.open(); onClick() },
        modifier = modifier
            .width(w)
            // Lambda overload on purpose: `lift` is an animated state, and the
            // non-lambda `offset(y =)` reads it during composition, so every
            // frame of a focus animation recomposes the card. The lambda is
            // read in the layout phase instead, which on a rail of thirty
            // posters is the difference between a smooth lift and a stutter.
            .offset { IntOffset(0, lift.roundToPx()) }
            .shadow(
                elevation = elevation,
                shape = RoundedCornerShape(14.dp),
                clip = false,
                ambientColor = Color.Black,
                // Focus tints the cast shadow with the brand accent, so the
                // glow reads as light coming off the card rather than a
                // generic drop shadow.
                spotColor = if (focused) EnktelBlue else Color.Black,
            )
            .tapClick { NavSounds.open(); onClick() }.onFocusChanged {
            val wasFocused = focused
            focused = it.isFocused
            if (!wasFocused && it.isFocused) NavSounds.click()
            focusedPoster?.report(it.isFocused, imageUrl, tmdbId, isSeries, title)
        },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
        ),
        // v1.35.0 — ring width and scale come from the active palette's focus
        // tokens instead of being pinned here. Deep Space specifies a tighter
        // 2 dp ring at 1.05×; the older palettes keep the v1.30.0 4 dp / 1.08×
        // treatment via the token defaults, so nothing regresses for a user
        // who has picked one of them.
        scale = ClickableSurfaceDefaults.scale(focusedScale = EnktelFocusScale),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(EnktelFocusRingWidth, EnktelBlue),
                shape = RoundedCornerShape(14.dp),
            ),
        ),
    ) {
        // Cinematic poster treatment: image fills the card, title/subtitle sit on a
        // bottom gradient scrim baked into the artwork (Netflix-style caption).
        Box(
            Modifier
                .width(w)
                .height(h)
                .clip(RoundedCornerShape(14.dp))
                .background(EnktelSurfaceHigh)
                .then(
                    if (focused) {
                        Modifier.shadow(
                            elevation = EnktelFocusGlowRadius,
                            shape = RoundedCornerShape(14.dp),
                            spotColor = EnktelFocusGlow,
                        )
                    } else Modifier
                ),
        ) {
            if (imageUrl.isNotBlank()) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(listOf(EnktelBlue.copy(0.3f), EnktelPurple.copy(0.3f)))
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        title.take(2).uppercase(),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White.copy(0.7f),
                    )
                }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.55f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.88f),
                        )
                    ),
            )
            Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                Text(
                    title, fontSize = if (wide) 13.sp else 14.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = Color.White.copy(alpha = 0.75f),
                    )
                }
            }
        }
    }
}

/** Small circular/rounded thumbnail with a text-initial fallback, used for compact list rows (e.g. DVR manager). */
@Composable
fun ThumbBox(label: String, imageUrl: String, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 56.dp) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(EnktelSurfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl.isNotBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = label,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(4.dp),
            )
        } else {
            Text(
                label.take(2).uppercase(),
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                color = Color.White.copy(0.7f),
            )
        }
    }
}

/** DPAD/touch-friendly confirmation overlay, consistent with the app's existing full-screen dialog pattern. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = "Confirm",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(0.65f)).tapClick(onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            onClick = {},
            modifier = Modifier.padding(horizontal = 24.dp).widthIn(max = 420.dp).fillMaxWidth(),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
            colors = ClickableSurfaceDefaults.colors(containerColor = EnktelSurfaceHigh, contentColor = Color.White),
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(8.dp))
                Text(message, fontSize = 13.sp, color = EnktelTextDim)
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FocusButton(confirmLabel, accent = true, onClick = onConfirm)
                    FocusButton("Cancel", onClick = onDismiss)
                }
            }
        }
    }
}

@Composable
fun <T> ContentRail(
    title: String,
    items: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    accent: Color = EnktelBlue,
    subtitle: String = "",
    itemContent: @Composable (T) -> Unit,
) {
    if (items.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        // Netflix-grade rail heading: a coloured accent bar, chunky title, muted item count.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 48.dp, end = 48.dp, bottom = 6.dp, top = 4.dp),
        ) {
            Box(
                Modifier
                    .height(20.dp)
                    .width(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.55f)))),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                title,
                fontSize = 19.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 0.3.sp,
            )
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.width(10.dp))
                Text(subtitle, fontSize = 12.sp, color = EnktelTextDim, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.weight(1f))
            Text(
                "${items.size}",
                fontSize = 12.sp,
                color = EnktelTextDim,
                fontWeight = FontWeight.SemiBold,
            )
        }
        LazyRow(
            // Vertical padding is load-bearing, not decoration: a focused card
            // lifts 6 dp and casts an 18 dp shadow, and a LazyRow clips to its
            // own bounds — without headroom the glow is sliced off flat along
            // the rail edge, which looks worse than having no shadow at all.
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 14.dp),
            // 14 dp was tight once cards gained a cast shadow; neighbouring
            // shadows overlapped and the row read as one mass rather than
            // separate cards.
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(items, key = key) { itemContent(it) }
        }
    }
}

@Composable
fun Badge(text: String, color: Color = EnktelBlue) {
    Box(
        Modifier
            .background(color.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text, fontSize = 10.sp, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ProgressBarThin(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color.White.copy(alpha = 0.2f)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Transparent),
        )
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(4.dp)
                .background(Brush.horizontalGradient(listOf(EnktelBlue, EnktelPurple))),
        )
    }
}

@Composable
fun CenterMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = EnktelTextDim, fontSize = 15.sp)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = modifier)
}

@Composable
fun PinDialog(
    title: String,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 340.dp)
                .fillMaxWidth()
                .background(EnktelSurfaceHigh, RoundedCornerShape(12.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            TvTextField(pin, { pin = it.filter(Char::isDigit).take(8) }, "PIN", password = true)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FocusButton("Unlock", accent = true, onClick = { onSubmit(pin) })
                FocusButton("Cancel", onClick = onDismiss)
            }
        }
    }
}

@Composable
fun KeyValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = EnktelTextDim, fontSize = 12.sp, modifier = Modifier.width(140.dp))
        Text(value, color = Color.White, fontSize = 12.sp)
    }
}
