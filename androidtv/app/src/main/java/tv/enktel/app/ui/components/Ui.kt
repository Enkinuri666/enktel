package tv.enktel.app.ui.components

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.ClickableSurfaceDefaults
import coil.compose.AsyncImage
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelPurple
import tv.enktel.app.ui.theme.EnktelSurfaceHigh
import tv.enktel.app.ui.theme.EnktelTextDim

@Composable
fun FocusButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
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

@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
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
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
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
) {
    val w = if (wide) 240.dp else 130.dp
    val h = if (wide) 135.dp else 190.dp
    Surface(
        onClick = onClick,
        modifier = modifier.width(w),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, EnktelBlue),
                shape = RoundedCornerShape(10.dp),
            ),
        ),
    ) {
        Column {
            Box(
                Modifier
                    .width(w)
                    .height(h)
                    .clip(RoundedCornerShape(10.dp))
                    .background(EnktelSurfaceHigh),
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
            }
            Spacer(Modifier.height(6.dp))
            Text(title, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White)
            if (subtitle.isNotBlank()) {
                Text(subtitle, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = EnktelTextDim)
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
    itemContent: @Composable (T) -> Unit,
) {
    if (items.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        Text(
            title,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(start = 48.dp, bottom = 10.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
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
fun KeyValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = EnktelTextDim, fontSize = 12.sp, modifier = Modifier.width(140.dp))
        Text(value, color = Color.White, fontSize = 12.sp)
    }
}
