package tv.enktel.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import coil3.compose.AsyncImage
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelFocusGlow
import tv.enktel.app.ui.theme.EnktelFocusGlowRadius
import tv.enktel.app.ui.theme.EnktelFocusRingWidth
import tv.enktel.app.ui.theme.EnktelFocusScale
import tv.enktel.app.ui.theme.EnktelPurple
import tv.enktel.app.ui.theme.EnktelSurfaceHigh
import tv.enktel.app.ui.theme.EnktelText
import tv.enktel.app.ui.theme.EnktelTextDim
import tv.enktel.app.ui.theme.EnktelTextOnArt
import tv.enktel.app.ui.theme.EnktelTextOnArtDim
import tv.enktel.app.ui.theme.EnktelType

/**
 * TV-material surfaces only react to DPAD select; on touchscreens (phones, tablets,
 * touch-enabled boxes) taps land nowhere. Attach this alongside Surface(onClick) so
 * both input methods work.
 */
@Composable
fun Modifier.tapClick(onClick: () -> Unit): Modifier {
    // Touch gets a tick, the way the D-pad already gets an earcon.
    //
    // The TV path has NavSounds on every focus move and selection; the touch
    // path had nothing at all — no ripple through this modifier, and no
    // haptic — so on a phone the entire app confirmed a press only by whatever
    // happened next. On a slow panel fetch that is a second of silence after a
    // tap, which reads as a missed press and gets tapped again.
    //
    // ContextClick rather than LongPress: it is the lightest tick in the set,
    // appropriate for a confirmation rather than an alert, and it is the one
    // that stays pleasant when a user is moving quickly through a grid.
    val haptics = LocalHapticFeedback.current
    return pointerInput(Unit) {
        detectTapGestures(
            onTap = {
                runCatching { haptics.performHapticFeedback(HapticFeedbackType.ContextClick) }
                onClick()
            },
        )
    }
}

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
            style = EnktelType.label,
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
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(24.dp)),
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
                shape = RoundedCornerShape(24.dp),
            ),
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White),
                shape = RoundedCornerShape(24.dp),
            ),
        ),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = EnktelType.caption,
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
        style = EnktelType.overline,
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
    val keyboard = LocalSoftwareKeyboardController.current
    val isMobile = tv.enktel.app.BuildConfig.FLAVOR == "mobile"

    // Focus alone must not open the keyboard.
    //
    // Compose raises the IME the moment a BasicTextField gains focus. On a TV
    // the app moves focus *programmatically* — every screen asks for focus on
    // mount so the D-pad has somewhere to start — and on any screen whose
    // first focusable is a search box that meant the keyboard appeared by
    // itself, on top of whatever was playing. Setting windowSoftInputMode in
    // the manifest did not fix it, because the window flag governs the
    // activity's initial state, not an IME raised later by a focus change.
    //
    // So on TV the field is read-only until the user presses Select on it,
    // which is how every set-top box behaves: focus highlights, OK opens the
    // keyboard, Back closes it. On touch, focus only ever comes *from* a tap,
    // so the tap is the intent and the keyboard opens as usual.
    var typing by remember { mutableStateOf(false) }
    val editable = isMobile || typing
    LaunchedEffect(typing) { if (typing) keyboard?.show() else keyboard?.hide() }
    LaunchedEffect(focused) { if (!focused) typing = false }

    Column(modifier.fillMaxWidth()) {
        Text(label, color = EnktelTextDim, style = EnktelType.caption)
        Spacer(Modifier.height(4.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            readOnly = !editable,
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            textStyle = EnktelType.subtitle.copy(color = EnktelText, fontSize = 16.sp),
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
                        // Select opens the keyboard; Back closes it and hands
                        // the D-pad back to the screen rather than exiting.
                        AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                        AndroidKeyEvent.KEYCODE_ENTER,
                        AndroidKeyEvent.KEYCODE_NUMPAD_ENTER ->
                            if (!typing) { typing = true; true } else false
                        AndroidKeyEvent.KEYCODE_BACK ->
                            if (typing) { typing = false; true } else false
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
    /**
     * 1-based position in a ranked rail (Trending, Top Rated, Top Picks).
     * Non-zero draws the outlined numeral; 0 leaves the artwork alone.
     */
    rank: Int = 0,
    /**
     * How far through the title the viewer is, 0f..1f. Draws the resume bar
     * along the bottom edge. 0f draws nothing, which is what every card that
     * has never been played should pass.
     */
    progress: Float = 0f,
    /**
     * Text to sniff for a streaming-service or network brand — usually the
     * catalogue category the title came from. A match draws the platform's
     * badge on the artwork; anything unrecognised draws nothing.
     */
    platformHint: String = "",
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
            // NB: no .shadow() here. This is the misaligned "glowing box".
            //
            // tv-material applies focusedScale to the Surface's own graphics
            // layer. A .shadow() written on the modifier chain *outside* that
            // Surface is not inside the scaled layer, so on focus it kept
            // drawing at 100 % while the bordered card drew at 105–108 % — a
            // brand-tinted rectangle sitting visibly offset from the card it
            // was supposed to be lighting, growing more wrong the further from
            // the screen centre the card sat.
            //
            // This is the same fault that was found and fixed in NavRailItem;
            // the fix was never carried across to the posters, which is why the
            // misaligned box was still being reported after the rail was clean.
            // The glow now lives on the inner Box below, inside the scaled
            // layer, so it shares one set of bounds with the border.
            .tapClick { NavSounds.open(); onClick() }.onFocusChanged {
            val wasFocused = focused
            focused = it.isFocused
            if (!wasFocused && it.isFocused) NavSounds.click()
            focusedPoster?.report(it.isFocused, imageUrl, tmdbId, isSeries, title)
        },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
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
                shape = RoundedCornerShape(16.dp),
            ),
        ),
    ) {
        // Cinematic poster treatment: image fills the card, title/subtitle sit on a
        // bottom gradient scrim baked into the artwork (Netflix-style caption).
        Box(
            Modifier
                .width(w)
                .height(h)
                // Inside the scaled layer, so the glow tracks the card exactly.
                // `elevation` carries the resting depth and focus adds the
                // brand tint, which is what the two separate shadows used to do
                // between them — one of which was drawing at the wrong size.
                .shadow(
                    elevation = if (focused) EnktelFocusGlowRadius else elevation,
                    shape = RoundedCornerShape(16.dp),
                    clip = false,
                    ambientColor = Color.Black,
                    spotColor = if (focused) EnktelFocusGlow else Color.Black,
                )
                .clip(RoundedCornerShape(16.dp))
                .background(EnktelSurfaceHigh),
        ) {
            if (imageUrl.isNotBlank()) {
                // Posters used to pop in from a flat grey plate.
                //
                // Shimmer.kt has held a skeleton treatment since v1.19 and
                // nothing ever called it — `shimmer()` and `PosterSkeleton`
                // had zero call sites in the whole app. On a Fire TV Stick
                // fetching thirty posters over a domestic connection that is
                // the first two seconds of every rail: a wall of identical
                // dead rectangles, which is the single cheapest-looking
                // moment in the product.
                //
                // The animation is bounded by load state rather than run
                // unconditionally behind loaded art — an infinite transition
                // per card, thirty cards to a rail, is exactly the kind of
                // thing that costs frames on the hardware this has to run on.
                var loading by remember(imageUrl) { mutableStateOf(true) }
                if (loading) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .shimmer(
                                baseColor = EnktelSurfaceHigh,
                                highlightColor = EnktelBlue.copy(alpha = 0.10f),
                            ),
                    )
                }
                AsyncImage(
                    model = imageUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    onState = { loading = it is coil3.compose.AsyncImagePainter.State.Loading },
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
                        color = EnktelTextOnArt.copy(0.7f),
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
            // Rank numeral, drawn over the art at the leading edge.
            //
            // The hero banner has said "#3 TOP 10" since v1.27 and the rails
            // that are literally ranked orderings — Trending, Top Rated, Top
            // Picks — said nothing, so their order carried no meaning a viewer
            // could see. Outlined rather than filled because a solid numeral
            // this large fights the artwork it sits on; a stroke reads as a
            // number laid over a poster instead of a number stamped into it.
            if (rank > 0) {
                Text(
                    "$rank",
                    style = TextStyle(
                        fontSize = if (wide) 44.sp else 54.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White.copy(alpha = 0.92f),
                        drawStyle = Stroke(width = 3f),
                    ),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 6.dp, top = 2.dp),
                )
            }
            // Platform badge. StreamingBadges has recognised eighty services
            // and networks since v1.22 and was wired into exactly one screen
            // (Manage Categories), so a catalogue full of "Netflix — Action"
            // rows rendered the brand nowhere a viewer would ever browse.
            if (platformHint.isNotBlank()) {
                PlatformBadgeFor(
                    platformHint,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                )
            }
            Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                Text(
                    title, fontSize = if (wide) 13.sp else 14.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, color = EnktelTextOnArt,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle, style = EnktelType.caption, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, color = EnktelTextOnArtDim,
                    )
                }
            }
            // Resume bar, flush to the bottom edge.
            //
            // Continue Watching encoded progress as " · 45%" appended to the
            // subtitle at 11 sp — a figure nobody reads from a sofa, on the one
            // rail whose entire purpose is "how far through am I". The bar is
            // the idiom every streaming service uses because it answers the
            // question pre-attentively, without the viewer parsing anything.
            if (progress > 0f) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color.Black.copy(alpha = 0.55f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(4.dp)
                            .background(
                                Brush.horizontalGradient(listOf(EnktelBlue, EnktelPurple)),
                            ),
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
                color = EnktelText.copy(0.7f),
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
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
            colors = ClickableSurfaceDefaults.colors(containerColor = EnktelSurfaceHigh, contentColor = Color.White),
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(title, style = EnktelType.title, color = EnktelText)
                Spacer(Modifier.height(8.dp))
                Text(message, style = EnktelType.body, color = EnktelTextDim)
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
) = ContentRailIndexed(title, items, key, modifier, accent, subtitle) { _, item ->
    itemContent(item)
}

/**
 * [ContentRail] where the item builder is handed its position, so a rail that
 * *is* an ordering — Trending, Top Rated, Top Picks — can pass the rank down
 * to [PosterCard] and have the numeral drawn on the artwork.
 *
 * This exists as a second entry point rather than a changed signature because
 * `ContentRail` has around thirty call sites and only three of them are ranked.
 */
@Composable
fun <T> ContentRailIndexed(
    title: String,
    items: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    accent: Color = EnktelBlue,
    subtitle: String = "",
    itemContent: @Composable (Int, T) -> Unit,
) {
    if (items.isEmpty()) return
    // focusGroup() is what makes DOWN work on this screen.
    //
    // Without it, every card in every rail is a flat peer in one focus
    // search. Compose then picks the best candidate in the requested
    // direction by geometry — and because a rail is far wider than it is
    // tall, a card down-and-to-the-side routinely scores better than the one
    // directly below. The visible result is a DOWN press that moves
    // sideways, which is exactly what it looked like. Holding DOWN
    // eventually escaped only because the repeat drove the list to scroll and
    // compose a fresh rail.
    //
    // Grouping makes the rail a single stop: the parent search sees one
    // target per rail and steps between them vertically. focusRestorer() then
    // returns to the card you were last on when you come back up, instead of
    // snapping to the start of the row.
    Column(modifier.fillMaxWidth().focusGroup()) {
        // Netflix-grade rail heading: a coloured accent bar, chunky title, muted item count.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 48.dp, end = 48.dp, bottom = 6.dp, top = 4.dp),
        ) {
            Box(
                Modifier
                    .height(20.dp)
                    .width(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.55f)))),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                title,
                style = EnktelType.title,
                color = EnktelText,
            )
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.width(10.dp))
                Text(subtitle, style = EnktelType.caption, color = EnktelTextDim)
            }
            Spacer(Modifier.weight(1f))
            Text(
                "${items.size}",
                style = EnktelType.caption,
                color = EnktelTextDim,
            )
        }
        LazyRow(
            modifier = Modifier.focusRestorer(),
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
            itemsIndexed(items, key = { _, item -> key(item) }) { i, item -> itemContent(i, item) }
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
        Text(text, style = EnktelType.overline, color = color)
    }
}

@Composable
fun ProgressBarThin(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(4.dp)
            .clip(RoundedCornerShape(4.dp))
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
        Text(text, color = EnktelTextDim, style = EnktelType.subtitle)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = EnktelType.headline, color = EnktelText, modifier = modifier)
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
            Text(title, color = EnktelText, style = EnktelType.title)
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
        Text(label, color = EnktelTextDim, style = EnktelType.caption, modifier = Modifier.width(140.dp))
        Text(value, color = EnktelText, style = EnktelType.caption)
    }
}

/**
 * Hands a link to whatever the device shares with.
 *
 * The link is the https one rather than the `enktel://` scheme, because that
 * is the half that survives a messaging app: a chat client linkifies an http(s)
 * URL and leaves a custom scheme as plain text, so a friend without the app
 * gets a web page instead of a dead tap. Android's app-links filter claims it
 * back for EnkTel when it is installed.
 *
 * Silent when nothing on the device can share — a bare Fire TV Stick has no
 * share target at all, and a button that reports failure it cannot fix is
 * worse than one that just isn't there.
 */
/**
 * Fires the system share sheet for [target]. Returns false when the device has
 * nothing to share with, so the caller can fall back.
 *
 * The link is the https one rather than the `enktel://` scheme, because that is
 * the half that survives a messaging app: a chat client linkifies an http(s)
 * URL and leaves a custom scheme as plain text, so a friend without the app
 * installed gets a web page instead of a dead tap. Android's app-links filter
 * claims it back for EnkTel when it is installed.
 */
fun shareTarget(context: android.content.Context, target: tv.enktel.app.DeepLink.Target): Boolean {
    val url = tv.enktel.app.DeepLink.shareUrl(target)
    val text = if (target.name.isBlank()) url else "${target.name}\n$url"
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, text)
        putExtra(android.content.Intent.EXTRA_SUBJECT, target.name)
    }
    val chooser = android.content.Intent.createChooser(send, "Share with")
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { context.startActivity(chooser); true }.getOrDefault(false)) return true
    // A bare Fire TV Stick has no share target at all. The clipboard is on
    // every device, so the link still goes somewhere the user can use.
    val clip = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
        as? android.content.ClipboardManager ?: return false
    clip.setPrimaryClip(android.content.ClipData.newPlainText(target.name, url))
    return false
}

@Composable
fun ShareButton(
    target: tv.enktel.app.DeepLink.Target,
    label: String = "\u2197  Share",
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val toaster = LocalToaster.current
    FocusButton(label, onClick = {
        if (!shareTarget(context, target)) toaster.info("Link copied to the clipboard")
    })
}
