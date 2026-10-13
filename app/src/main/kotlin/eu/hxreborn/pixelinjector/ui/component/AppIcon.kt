package eu.hxreborn.pixelinjector.ui.component

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

private val RowGlyphSize = 24.dp
private val HeaderGlyphSize = 20.dp
private const val LAYER_PX = 432
private const val GLYPH_PADDING_PERCENT = 8

private class Loaded(
    val bitmap: ImageBitmap?,
)

private object MonochromeIcons {
    private val cache = ConcurrentHashMap<String, Loaded>()

    fun peek(packageName: String): Loaded? = cache[packageName]

    fun load(
        context: Context,
        packageName: String,
    ): Loaded = cache.getOrPut(packageName) { Loaded(render(context, packageName)) }

    private fun render(
        context: Context,
        packageName: String,
    ): ImageBitmap? {
        val icon = runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
        val mono = (icon as? AdaptiveIconDrawable)?.monochrome ?: return null
        val layer = Bitmap.createBitmap(LAYER_PX, LAYER_PX, Bitmap.Config.ARGB_8888)
        mono.setBounds(0, 0, LAYER_PX, LAYER_PX)
        mono.draw(Canvas(layer))
        return cropToGlyph(layer)?.asImageBitmap()
    }

    private fun cropToGlyph(layer: Bitmap): Bitmap? {
        val pixels = IntArray(LAYER_PX * LAYER_PX)
        layer.getPixels(pixels, 0, LAYER_PX, 0, 0, LAYER_PX, LAYER_PX)
        var left = LAYER_PX
        var top = LAYER_PX
        var right = -1
        var bottom = -1
        for (y in 0 until LAYER_PX) {
            for (x in 0 until LAYER_PX) {
                if (pixels[y * LAYER_PX + x] ushr 24 == 0) continue
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
        if (right < 0) return null
        val glyph = maxOf(right - left, bottom - top) + 1
        val side = glyph + glyph * GLYPH_PADDING_PERCENT * 2 / 100
        val square = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        Canvas(square).drawBitmap(
            layer,
            (side - (right - left + 1)) / 2f - left,
            (side - (bottom - top + 1)) / 2f - top,
            null,
        )
        return square
    }
}

@Composable
fun AppGlyph(
    packageName: String?,
    fallback: ImageVector?,
) {
    val context = LocalContext.current.applicationContext
    val loaded by produceState(packageName?.let(MonochromeIcons::peek), packageName) {
        value = packageName?.let { withContext(Dispatchers.IO) { MonochromeIcons.load(context, it) } }
    }
    val modifier = Modifier.size(HeaderGlyphSize)
    val tint = MaterialTheme.colorScheme.primary
    val bitmap = loaded?.bitmap
    when {
        bitmap != null -> Icon(bitmap, contentDescription = null, modifier = modifier, tint = tint)
        loaded != null && fallback != null -> Icon(fallback, contentDescription = null, modifier = modifier, tint = tint)
        else -> Spacer(modifier)
    }
}

@Composable
fun RowScope.TileLeading(symbol: ImageVector?) {
    val modifier = Modifier.size(RowGlyphSize)
    if (symbol == null) {
        Spacer(modifier)
    } else {
        Icon(symbol, contentDescription = null, modifier = modifier, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
