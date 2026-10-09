package com.reelcounter.core.design.components

import android.content.pm.PackageManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.reelcounter.core.design.theme.RcTheme
import com.reelcounter.core.model.ReelApp

/**
 * The app's own launcher icon, read from the phone, so it's always the current official icon and we
 * ship none of their artwork. When the app isn't installed we draw a simple stand-in badge.
 */
@Composable
fun AppIcon(app: ReelApp, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val bitmap: ImageBitmap? = remember(app, sizePx) {
        try {
            context.packageManager.getApplicationIcon(app.packageName).toBitmap(sizePx, sizePx).asImageBitmap()
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }
    val tokens = RcTheme.tokens
    val shape = RoundedCornerShape(size * 0.28f)
    val outline = if (tokens.isSticker) Modifier.border(2.dp, tokens.borderColor, shape) else Modifier
    Box(
        modifier
            .size(size)
            .clip(shape)
            .then(outline)
            .semantics { contentDescription = app.displayName },
    ) {
        if (bitmap != null) {
            Image(bitmap, contentDescription = null, modifier = Modifier.size(size))
        } else {
            FallbackBadge(app, Modifier.size(size))
        }
    }
}

@Composable
private fun FallbackBadge(app: ReelApp, modifier: Modifier) {
    Canvas(modifier) {
        val s = size.minDimension
        when (app) {
            ReelApp.INSTAGRAM -> {
                drawRoundRect(
                    brush = Brush.linearGradient(
                        listOf(Color(0xFFFEDA75), Color(0xFFFA7E1E), Color(0xFFD62976), Color(0xFF962FBF), Color(0xFF4F5BD5)),
                        start = Offset(0f, s),
                        end = Offset(s, 0f),
                    ),
                    cornerRadius = CornerRadius(s * 0.28f),
                )
                val stroke = Stroke(width = s * 0.075f)
                drawRoundRect(Color.White, topLeft = Offset(s * 0.23f, s * 0.23f), size = Size(s * 0.54f, s * 0.54f), cornerRadius = CornerRadius(s * 0.17f), style = stroke)
                drawCircle(Color.White, radius = s * 0.13f, center = Offset(s / 2f, s / 2f), style = stroke)
                drawCircle(Color.White, radius = s * 0.04f, center = Offset(s * 0.67f, s * 0.33f))
            }
            ReelApp.YOUTUBE -> {
                drawRoundRect(Color.White, cornerRadius = CornerRadius(s * 0.28f))
                drawRoundRect(Color(0xFFFF0000), topLeft = Offset(s * 0.15f, s * 0.27f), size = Size(s * 0.7f, s * 0.47f), cornerRadius = CornerRadius(s * 0.14f))
                val play = Path().apply {
                    moveTo(s * 0.43f, s * 0.39f)
                    lineTo(s * 0.63f, s * 0.5f)
                    lineTo(s * 0.43f, s * 0.62f)
                    close()
                }
                drawPath(play, Color.White)
            }
        }
    }
}
